package com.ghouse.socialraven.dto;

public  class LinkedInUploadInfo {
        private final String asset;
        private final String uploadUrl;

        public LinkedInUploadInfo(String asset, String uploadUrl) {
            this.asset = asset;
            this.uploadUrl = uploadUrl;
        }

        public String getAsset() {
            return asset;
        }

        public String getUploadUrl() {
            return uploadUrl;
        }
    }