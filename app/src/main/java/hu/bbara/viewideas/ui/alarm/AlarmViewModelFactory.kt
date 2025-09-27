package hu.bbara.viewideas.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import hu.bbara.viewideas.data.alarm.AlarmRepository

class AlarmViewModelFactory(
    private val repository: AlarmRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            return AlarmViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
