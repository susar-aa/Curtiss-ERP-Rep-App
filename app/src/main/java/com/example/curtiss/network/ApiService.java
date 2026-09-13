package com.example.curtiss.network;

import com.example.curtiss.network.models.DashboardAnalyticsResponse;
import com.example.curtiss.network.models.LoginResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import com.example.curtiss.network.models.LoginRequest;

public interface ApiService {

    @POST("rep/RepDashboard/api_login?api_sync=1")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST
    Call<LoginResponse> loginWithUrl(@retrofit2.http.Url String url, @Body LoginRequest request);

    @GET("rep/RepDashboard/api_get_performance_analytics")
    Call<DashboardAnalyticsResponse> getPerformanceAnalytics(
            @Query("user_id") int userId,
            @Query("start_date") String startDate,
            @Query("end_date") String endDate
    );
}
