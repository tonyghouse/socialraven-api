package com.ghouse.socialraven.service.post.image;

import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class XImagePostPublisherService {

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private StorageService storageService;

    public void postImagesToX(PostEntity post, List<PostMediaEntity> mediaFiles,
                              OAuthInfoEntity authInfo) {
    }
}
