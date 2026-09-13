package com.example.curtiss.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.curtiss.BillingActivity;
import com.example.curtiss.repository.BillingRepository;
import java.util.List;

public class BillingViewModel extends AndroidViewModel {
    
    private final BillingRepository repository;
    private final MutableLiveData<BillingActivity.CustomerModel> selectedCustomer = new MutableLiveData<>();
    
    private LiveData<List<String>> categoriesLiveData;
    private LiveData<List<BillingActivity.CustomerModel>> customersLiveData;

    public BillingViewModel(@NonNull Application application) {
        super(application);
        repository = new BillingRepository(application);
        categoriesLiveData = repository.getCategories();
        customersLiveData = repository.getCustomers();
    }
    
    public LiveData<List<String>> getCategories() {
        return categoriesLiveData;
    }
    
    public LiveData<List<BillingActivity.CustomerModel>> getCustomers() {
        return customersLiveData;
    }
    
    public LiveData<BillingActivity.CustomerModel> getSelectedCustomer() {
        return selectedCustomer;
    }
    
    public void setSelectedCustomer(BillingActivity.CustomerModel customer) {
        selectedCustomer.setValue(customer);
    }
}
