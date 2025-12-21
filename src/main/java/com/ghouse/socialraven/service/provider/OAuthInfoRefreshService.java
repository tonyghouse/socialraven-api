package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
@Service
@Slf4j
public class OAuthInfoRefreshService {

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private YouTubeOAuthService youTubeOAuthService;

    @Autowired
    private ExecutorService virtualThreadExecutor; // injected bean

    public void refreshOAuthInfo(Long id) {

        OAuthInfoEntity oauthInfo = oAuthInfoRepo.findById(id).orElse(null);


        if(oauthInfo!=null){
            refreshToken(oauthInfo);
        }

    }

    /**
     * Performs up-to-10 retries and sends failure email if all fail.
     * Returns true if refresh succeeded; false otherwise.
     */
    private boolean refreshToken(OAuthInfoEntity oauthInfo) {
        boolean success = false;

        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                if (Provider.LINKEDIN.equals(oauthInfo.getProvider())) {
                    linkedInOAuthService.refreshAccessToken(oauthInfo);
                } else if (Provider.YOUTUBE.equals(oauthInfo.getProvider())) {
                    youTubeOAuthService.getValidOAuthInfo(oauthInfo);
                } else {
                    log.warn("Unknown provider for id {}: {}", oauthInfo.getId(), oauthInfo.getProvider());
                }

                success = true;
                break;

            } catch (Exception ex) {
                log.info("Retry {} failed for OAuthInfo ID {} due to: {}", attempt, oauthInfo.getId(), ex.getMessage());

                // Backoff between attempts. This blocks the virtual thread (cheap).
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(30)); // you can reduce this
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!success) {
           throw new RuntimeException("Failed to refresh token oauth info id: "+ oauthInfo.getId());
        }

        return success;
    }




}
