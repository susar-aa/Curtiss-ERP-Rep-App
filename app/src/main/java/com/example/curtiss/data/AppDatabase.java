package com.example.curtiss.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.curtiss.data.dao.CustomerDao;
import com.example.curtiss.data.dao.ProductDao;
import com.example.curtiss.data.entity.*;

@Database(entities = {
        ProductEntity.class,
        CategoryEntity.class,
        CustomerEntity.class,
        DailyRouteEntity.class,
        InvoiceEntity.class,
        InvoiceItemEntity.class,
        ServerRouteEntity.class,
        PaymentTermEntity.class,
        PaymentEntity.class,
        CreditInvoiceEntity.class,
        DiscountRuleEntity.class,
        DiscountRuleTierEntity.class,
        ImageDownloadQueueEntity.class,
        SyncLogEntity.class,
        ConflictBackupEntity.class,
        UnproductiveVisitEntity.class,
        RepresentativeEntity.class
}, version = 15)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract ProductDao productDao();
    public abstract CustomerDao customerDao();
    
    public static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Room will automatically take over the existing schema.
            // Since the tables already match exactly, no structural changes are needed here.
            // Room creates its own room_master_table internally to track hashes.
        }
    };
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "curtiss_offline.db")
                            .addMigrations(MIGRATION_14_15)
                            .allowMainThreadQueries() // Temporarily allowed for hybrid migration safety
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
