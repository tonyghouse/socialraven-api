package com.ghouse.socialraven.service.post.video;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.post.image.LinkedInImagePostPublisherService;
import com.ghouse.socialraven.service.post.image.XImagePostPublisherService;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.OAuthInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class VideoPostPublisherService {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;


    @Autowired
    private LinkedInVideoPostPublisherService linkedInVideoPostPublisherService;

    @Autowired
    private XVideoPostPublisherService xVideoPostPublisherService;

    @Autowired
    private OAuthInfoService oAuthInfoService;


    public void publishPost(PostEntity post) {
        try{
            String userId = post.getUserId();
            log.info("Publishing Video(s) post, title:{} for userId: {} ", post.getTitle(), userId);
            List<String> providerUserIds = post.getProviderUserIds();

            List<OAuthInfoEntity> oauthInfos = oAuthInfoService.getOAuthInfos(userId, providerUserIds);
            List<PostMediaEntity> mediaFiles = post.getMediaFiles();
            for(var authInfo : oauthInfos){
                if(Provider.LINKEDIN.equals(authInfo.getProvider())){
                    linkedInVideoPostPublisherService.postVideosToLinkedIn(post,mediaFiles, authInfo);
                }
                if(Provider.X.equals(authInfo.getProvider())){
                    xVideoPostPublisherService.postVideosToX(post,mediaFiles, authInfo);
                }
            }


            post.setPostStatus(PostStatus.POSTED);
            postRepo.save(post);
        } catch (Exception exp){
            throw new RuntimeException("Failed to Video Post: "+post.getId(), exp);
        }

    }

}
