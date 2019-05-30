package com.example.hp.rxjavanotesapp.model;

import com.google.gson.annotations.SerializedName;

public class User extends BaseResponse{
    @SerializedName("api_key")
    String apiKey;

    public String getApiKey() {
        return apiKey;
    }
}
