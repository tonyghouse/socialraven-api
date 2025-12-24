package com.ghouse.socialraven.service.post.text;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.provider.OAuthInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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

            PostCollectionEntity postCollection  = post.getPostCollection();
            String userId = postCollection.getUserId();
            log.info("Publishing Text post, title:{} for userId: {} ", postCollection.getTitle(), userId);

            OAuthInfoEntity authInfo = oAuthInfoService.getOAuthInfo(userId, post.getProviderUserId());
            List<PostMediaEntity> mediaFiles = postCollection.getMediaFiles();
            if (Provider.LINKEDIN.equals(authInfo.getProvider())) {
                linkedinTextPostPublisherService.postTextToLinkedin(post, authInfo, postCollection);
            }
            if (Provider.X.equals(authInfo.getProvider())) {
                xTextPostPublisherService.postTextToX(post, authInfo, postCollection);
            }

            post.setPostStatus(PostStatus.POSTED);
            postRepo.save(post);
        } catch (Exception exp){
            throw new RuntimeException("Failed to Text Post: "+post.getId(), exp);
        }

    }


}
