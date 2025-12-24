package com.ghouse.socialraven.service.post.video;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
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
    private YoutubeVideoPostPublisherService youtubeVideoPostPublisherService;

    @Autowired
    private OAuthInfoService oAuthInfoService;


    public void publishPost(PostEntity post) {
        try{
            PostCollectionEntity postCollection = post.getPostCollection();
            String userId = postCollection.getUserId();
            log.info("Publishing Video(s) post, title:{} for userId: {} ", postCollection.getTitle(), userId);

            OAuthInfoEntity authInfo = oAuthInfoService.getOAuthInfo(userId, post.getProviderUserId());
            List<PostMediaEntity> mediaFiles = postCollection.getMediaFiles();
            if (Provider.LINKEDIN.equals(authInfo.getProvider())) {
                linkedInVideoPostPublisherService.postVideosToLinkedIn(post, mediaFiles, authInfo, postCollection);
            }
            if (Provider.X.equals(authInfo.getProvider())) {
                xVideoPostPublisherService.postVideosToX(post, mediaFiles, authInfo, postCollection);
            }

            if(Provider.YOUTUBE.equals(authInfo.getProvider())){
                youtubeVideoPostPublisherService.postVideoToYoutube(post,mediaFiles, authInfo, postCollection);
            }


            post.setPostStatus(PostStatus.POSTED);
            postRepo.save(post);
        } catch (Exception exp){
            throw new RuntimeException("Failed to Video Post: "+post.getId(), exp);
        }

    }

}
