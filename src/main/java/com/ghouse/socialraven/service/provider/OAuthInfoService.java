package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OAuthInfoService {


    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private YouTubeOAuthService youTubeOAuthService;


    public OAuthInfoEntity getOAuthInfo(String userId, String providerUserId) {

        OAuthInfoEntity oauthInfo = oAuthInfoRepo.findByUserIdAndProviderUserId(userId, providerUserId);
        if(oauthInfo == null){
            throw new SocialRavenException("OAuthInfo not found", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (Provider.LINKEDIN.equals(oauthInfo.getProvider())) {
            return linkedInOAuthService.getValidOAuthInfo(oauthInfo);
        }

        if (Provider.X.equals(oauthInfo.getProvider())) {
            return xOAuthService.getValidOAuthInfo(oauthInfo);
        }

        if (Provider.YOUTUBE.equals(oauthInfo.getProvider())) {
            return youTubeOAuthService.getValidOAuthInfo(oauthInfo);
        }


        throw new SocialRavenException("No Provider found for OAuthInfo", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
