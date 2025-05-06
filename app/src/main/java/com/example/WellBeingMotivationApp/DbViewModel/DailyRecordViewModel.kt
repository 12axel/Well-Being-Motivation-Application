package com.example.WellBeingMotivationApp.DbViewModel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.WellBeingMotivationApp.roomDb.DailyRecord
import kotlinx.coroutines.launch

class DailyRecordViewModel(private val repository: Repository, context: Context): ViewModel() {
    private var sharedPreferences: SharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private var _midnightFlag = MutableLiveData<Boolean>()
    val midnightFlag: LiveData<Boolean> get() = _midnightFlag

    init{
        _midnightFlag.value = sharedPreferences.getBoolean("midnight_flag", false)

        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            if(key == "midnight_flag") {
                _midnightFlag.value = sharedPreferences.getBoolean("midnight_flag", false)
            }
        }
    }

    fun updateMidnightFlag(flag: Boolean){
        sharedPreferences.edit().putBoolean("midnight_flag", flag).apply()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener { _, _ ->  }
    }

    fun getTop5Records() = repository.getTop5Records().asLiveData(viewModelScope.coroutineContext)

    fun getRecordForToday(date: String) = repository.getRecordForToday(date).asLiveData(viewModelScope.coroutineContext)

    fun insertRecord(dailyRecord: DailyRecord){
        viewModelScope.launch {
            repository.insertRecord(dailyRecord)
        }
    }

    fun updateRecord(dailyRecord: DailyRecord){
        viewModelScope.launch {
            repository.updateRecord(dailyRecord)
        }
    }
}