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

    public void refreshOAuthInfos(List<Long> oauthInfoIds) {
        List<OAuthInfoEntity> oauthInfos = oAuthInfoRepo.findAllById(oauthInfoIds);

        List<java.util.concurrent.Future<Boolean>> futures = new ArrayList<>(oauthInfos.size());

        // submit one virtual-thread task per oauthInfo
        for (OAuthInfoEntity oauthInfo : oauthInfos) {
            futures.add(virtualThreadExecutor.submit(() -> {
                try {
                    return refreshToken(oauthInfo); // returns true on success, false on failure
                } catch (Exception e) {
                    log.error("Unhandled error while refreshing id {} : {}", oauthInfo.getId(), e.getMessage(), e);
                    // If refreshToken didn't already send email, do it here (defensive)
                    sendFailureEmail(oauthInfo);
                    return false;
                }
            }));
        }

        // wait for completion and log results (optional)
        for (int i = 0; i < futures.size(); i++) {
            var f = futures.get(i);
            var oauthInfo = oauthInfos.get(i);
            try {
                // wait indefinitely or choose a timeout per task
                boolean ok = f.get(); // you can also use get(timeout, unit)
                if (!ok) {
                    log.warn("Refresh failed for id {} after retries.", oauthInfo.getId());
                } else {
                    log.info("Refresh succeeded for id {}", oauthInfo.getId());
                }
            } catch (Exception e) {
                log.error("Error while waiting for refresh of id {}: {}", oauthInfo.getId(), e.getMessage(), e);
                // ensure email was sent
                sendFailureEmail(oauthInfo);
            }
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
                } else if (Provider.X.equals(oauthInfo.getProvider())) {
                    xOAuthService.refreshAccessToken(oauthInfo);
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
            sendFailureEmail(oauthInfo);
        }

        return success;
    }

    private void sendFailureEmail(OAuthInfoEntity oauthInfo) {
        // TODO: implement actual email sender later
        log.warn("Sending failure email for OAuthInfo id {}", oauthInfo.getId());
    }
}
