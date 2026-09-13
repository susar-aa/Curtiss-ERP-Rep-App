package com.example.curtiss.network.models;

import com.google.gson.annotations.SerializedName;

public class DashboardAnalyticsResponse {
    @SerializedName("success")
    public boolean success;
    
    @SerializedName("message")
    public String message;
    
    @SerializedName("data")
    public Data data;
    
    public static class Data {
        @SerializedName("overall_score")
        public double overallScore;
        
        @SerializedName("payroll")
        public Payroll payroll;
        
        @SerializedName("net_sales")
        public double netSales;
        
        @SerializedName("invoice_count")
        public int invoiceCount;
        
        @SerializedName("total_returns")
        public double totalReturns;
        
        @SerializedName("sales_target")
        public double salesTarget;
        
        @SerializedName("sales_needed_for_target")
        public double salesNeededForTarget;
        
        @SerializedName("avg_sales_needed_per_day")
        public double avgSalesNeededPerDay;
        
        @SerializedName("remaining_working_days")
        public int remainingWorkingDays;
        
        @SerializedName("total_collections")
        public double totalCollections;
        
        @SerializedName("collection_efficiency")
        public double collectionEfficiency;
        
        @SerializedName("collections_needed_for_target")
        public double collectionsNeededForTarget;
        
        @SerializedName("collection_target_pct")
        public double collectionTargetPct;
        
        @SerializedName("target_collection_amount")
        public double targetCollectionAmount;
        
        @SerializedName("total_outstanding")
        public double totalOutstanding;
        
        @SerializedName("productive_visits")
        public int productiveVisits;
        
        @SerializedName("targets")
        public Targets targets;
        
        @SerializedName("working_days")
        public int workingDays;
    }
    
    public static class Payroll {
        @SerializedName("base_salary")
        public double baseSalary;
        
        @SerializedName("total_earnings")
        public double totalEarnings;
        
        @SerializedName("sales_commission")
        public double salesCommission;
        
        @SerializedName("sales_incentive")
        public double salesIncentive;
        
        @SerializedName("productive_visits_bonus")
        public double productiveVisitsBonus;
        
        @SerializedName("productive_visits_bonus_rate")
        public double productiveVisitsBonusRate;
        
        @SerializedName("working_days_bonus")
        public double workingDaysBonus;
        
        @SerializedName("working_days_bonus_rate")
        public double workingDaysBonusRate;
        
        @SerializedName("collection_bonus")
        public double collectionBonus;
        
        @SerializedName("collection_bonus_rate")
        public double collectionBonusRate;
        
        @SerializedName("settings")
        public Settings settings;
    }
    
    public static class Settings {
        @SerializedName("sales_commission_pct")
        public double salesCommissionPct;
        
        @SerializedName("sales_incentive_pct")
        public double salesIncentivePct;
        
        @SerializedName("sales_incentive_max_limit")
        public double salesIncentiveMaxLimit;
    }
    
    public static class Targets {
        @SerializedName("productive_visits_target")
        public int productiveVisitsTarget;
        
        @SerializedName("working_days_target")
        public int workingDaysTarget;
    }
}
