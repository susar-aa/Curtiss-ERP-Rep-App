package com.example.curtiss.network.models;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("message")
    public String message;

    @SerializedName("token")
    public String token;

    @SerializedName("user")
    public User user;

    public static class User {
        @SerializedName("id")
        public int id;
        
        @SerializedName("employee_id")
        public int employeeId;
        
        @SerializedName("first_name")
        public String firstName;
        
        @SerializedName("last_name")
        public String lastName;
    }
}
