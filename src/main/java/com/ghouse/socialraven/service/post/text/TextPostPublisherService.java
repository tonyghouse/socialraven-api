package com.ghouse.socialraven.service.post.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.OAuthInfoService;
import com.ghouse.socialraven.service.provider.XOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TextPostPublisherService {

    @Autowired
     private PostRepo postRepo;

    @Autowired
     private OAuthInfoService oAuthInfoService;

    @Autowired
    private LinkedinTextPostPublisherService linkedinTextPostPublisherService;

    @Autowired
    private XTextPostPublisherService xTextPostPublisherService;


    public void publishPost(PostEntity post) {
        try{
            String userId = post.getUserId();
            log.info("Publishing Image(s) post, title:{} for userId: {} ", post.getTitle(), userId);
            List<String> providerUserIds = post.getProviderUserIds();

            List<OAuthInfoEntity> oauthInfos = oAuthInfoService.getOAuthInfos(userId, providerUserIds);
            List<PostMediaEntity> mediaFiles = post.getMediaFiles();
            for(var authInfo : oauthInfos){
                if(Provider.LINKEDIN.equals(authInfo.getProvider())){
                    linkedinTextPostPublisherService.postTextToLinkedin(post, authInfo);
                }
                if(Provider.X.equals(authInfo.getProvider())){
                    xTextPostPublisherService.postTextToX(post, authInfo);
                }
            }


            post.setPostStatus(PostStatus.POSTED);
            postRepo.save(post);
        } catch (Exception exp){
            throw new RuntimeException("Failed to Text Post: "+post.getId(), exp);
        }

    }


}
