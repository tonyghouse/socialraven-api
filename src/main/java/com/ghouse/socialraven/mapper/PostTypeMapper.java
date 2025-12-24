package com.ghouse.socialraven.mapper;

import com.ghouse.socialraven.constant.PostCollectionType;
import com.ghouse.socialraven.constant.PostType;

public class PostTypeMapper {

    public static PostType getPostTypeByPostCollectionType(PostCollectionType postCollectionType) {
        if (postCollectionType == null) {
            return null;
        }

        if (PostCollectionType.TEXT == postCollectionType) {
            return PostType.TEXT;
        }

        if (PostCollectionType.IMAGE == postCollectionType) {
            return PostType.IMAGE;
        }

        if (PostCollectionType.VIDEO == postCollectionType) {
            return PostType.VIDEO;
        }


        return null;
    }
}
