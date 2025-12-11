package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import org.springframework.beans.factory.annotation.Autowired;
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

    public List<OAuthInfoEntity> getOAuthInfos(String userId, List<String> providerUserIds) {
        List<OAuthInfoEntity> oauthInfos = oAuthInfoRepo.findByUserIdAndProviderUserIdIn(userId, providerUserIds);

        List<OAuthInfoEntity> validOAuthInfos = new ArrayList<>();
        for(var oauthInfo : oauthInfos){
            if(Provider.LINKEDIN.equals(oauthInfo.getProvider())){
                OAuthInfoEntity validOAuthInfo = linkedInOAuthService.getValidOAuthInfo(oauthInfo);
                validOAuthInfos.add(validOAuthInfo);
            }

            if(Provider.X.equals(oauthInfo.getProvider())){
                OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(oauthInfo);
                validOAuthInfos.add(validOAuthInfo);
            }

            if(Provider.YOUTUBE.equals(oauthInfo.getProvider())){
                OAuthInfoEntity validOAuthInfo = youTubeOAuthService.getValidOAuthInfo(oauthInfo);
                validOAuthInfos.add(validOAuthInfo);
            }

        }

        return validOAuthInfos;
    }
}
