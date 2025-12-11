package com.ghouse.socialraven.helper;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.constant.PostType;
import com.ghouse.socialraven.constant.Provider;

import java.util.Collections;
import java.util.List;

public class AllowedPostTypeFetcher {

    public static List<PostType> getAllowedPostTypes(Platform platform){
        if(Platform.x.equals(platform)){
            return List.of(PostType.TEXT, PostType.IMAGE,PostType.VIDEO);
        }

        if(Platform.linkedin.equals(platform)){
            return List.of(PostType.TEXT, PostType.IMAGE,PostType.VIDEO);
        }

        if(Platform.youtube.equals(platform)){
            return List.of(PostType.VIDEO);
        }

        if(Platform.instagram.equals(platform)){
            return List.of(PostType.IMAGE,PostType.VIDEO);
        }

        if(Platform.facebook.equals(platform)){
            return List.of(PostType.TEXT, PostType.IMAGE,PostType.VIDEO);
        }



        return Collections.emptyList();

    }
}
