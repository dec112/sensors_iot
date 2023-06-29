package run.jkdev.dec4iot.jetpack

import android.nfc.Tag
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NfcDataViewModel: ViewModel() {
    val nfcData = MutableLiveData<Tag>()
    val shouldBeListening: MutableLiveData<Boolean> = MutableLiveData(false)
}