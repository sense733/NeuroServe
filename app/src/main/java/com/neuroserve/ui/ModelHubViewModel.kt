package com.neuroserve.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroserve.R
import com.neuroserve.data.ModelInfo
import com.neuroserve.data.ModelRepository
import com.neuroserve.engine.EngineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ModelHubViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val engineManager: EngineManager
) : ViewModel() {

    val modelList: StateFlow<List<ModelInfo>> = modelRepository.modelList
    val isImporting: StateFlow<Boolean> = modelRepository.isImporting
    val importProgress: StateFlow<Float> = modelRepository.importProgress
    val activeModelName: StateFlow<String?> = engineManager.activeModelName

    fun refreshModelList(filesDir: File) {
        viewModelScope.launch {
            modelRepository.refreshModelList(filesDir)
        }
    }

    fun importModel(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = modelRepository.importModel(context, uri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_import_success, fileName), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            engineManager.loadModel(model.meta)
        }
    }
}
