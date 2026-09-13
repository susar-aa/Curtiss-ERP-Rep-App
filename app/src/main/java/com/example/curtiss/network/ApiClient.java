package com.example.curtiss.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.curtiss.SecurePreferences;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit = null;
    private static String currentBaseUrl = null;

    public static Retrofit getClient(final Context context) {
        SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        String baseUrl = prefs.getString("base_url", "https://falcon.trycurtiss.com");
        
        // Ensure trailing slash
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        // Rebuild retrofit if the base URL changed or if it's null
        if (retrofit == null || !baseUrl.equals(currentBaseUrl)) {
            currentBaseUrl = baseUrl;

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
                            String token = prefs.getString("api_token", "");
                            int userId = prefs.getInt("user_id", 0);

                            Request.Builder requestBuilder = chain.request().newBuilder()
                                    .addHeader("Accept", "application/json")
                                    .addHeader("X-User-ID", String.valueOf(userId));

                            if (!token.isEmpty()) {
                                requestBuilder.addHeader("Authorization", "Bearer " + token);
                            }

                            return chain.proceed(requestBuilder.build());
                        }
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
