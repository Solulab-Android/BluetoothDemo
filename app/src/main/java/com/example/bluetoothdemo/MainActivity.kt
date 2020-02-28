package com.example.bluetoothdemo

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothdemo.databinding.ItemBluetoothDeviceBinding
import com.github.nitrico.lastadapter.LastAdapter
import com.livinglifetechway.k4kotlin.core.hide
import com.livinglifetechway.k4kotlin.core.orFalse
import com.livinglifetechway.k4kotlin.core.show
import com.livinglifetechway.k4kotlin.core.toastNow
import com.livinglifetechway.quickpermissions.annotations.WithPermissions
import gun0912.tedimagepicker.builder.TedImagePicker
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.reflect.Method
import java.net.URLConnection
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    lateinit var mBlueToothAdapter: BluetoothAdapter
    private var isChecked = false
    private var discoveredDevices = arrayListOf<BluetoothDevice>()
    lateinit var mProgressDialog: ProgressDialog
    var mSelectedDevice: BluetoothDevice? = null
    val uuid: UUID = UUID.fromString("8989063a-c9af-463a-b3f1-f21d9b2b827b")
    var mByteArray: ByteArray? = null
    var totalByteList = ArrayList<Byte>()
    var receivedStream: FileOutputStream? = null
    var destFile: File? = null

    companion object {
        const val REQUEST_ENABLE_BT = 101
        const val MESSAGE_READ = 0
        const val MESSAGE_TOAST = 1
    }

    /**
     * Handler to read file bytes in small chunks
     */
    val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            var byteArray = ByteArray(msg.arg1)
            if (msg.what == MESSAGE_READ) {
                byteArray = msg.obj as ByteArray
                totalByteList.addAll(byteArray.toList())
                receivedStream!!.write(msg.obj as ByteArray, 0, msg.arg1)
            } else {

                receivedStream!!.flush()
                receivedStream!!.close()

                val myBitmap = BitmapFactory.decodeFile(destFile!!.absolutePath)
//                val myBitmap = BitmapFactory.decodeByteArray(
//                    totalByteList.toByteArray(),
//                    0,
//                    totalByteList.toByteArray().size
//                )
//                myBitmap.compress(Bitmap.CompressFormat.PNG, 90, receivedStream)


                runOnUiThread {
                    if (myBitmap != null) {
                        ivReceivedImage.setImageBitmap(myBitmap)
                        ivReceivedImage.show()
                    } else {
                        toastNow("Bitmap is null")
                    }
                }
            }
        }
    }


    //BroadcastReceiver for ACTION_FOUND.
    private val receiverDeviceDiscover = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    discoveredDevices.add(device)
                    recPairedDevices.adapter?.notifyDataSetChanged()
                    mProgressDialog.dismiss()
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    mProgressDialog.setMessage("Discovering Devices")
                    mProgressDialog.show()
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    mProgressDialog.dismiss()
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    val prevState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.ERROR
                    )

                    if (state == BluetoothDevice.BOND_BONDED) {
                    }

                    recPairedDevices.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    //BroadcastReceiver for ACTION_STATE_CHANGED
    private val receiverBluetoothStateChange: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        changeBluetoothInfo(false)
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                    }
                    BluetoothAdapter.STATE_ON -> {
                        changeBluetoothInfo(true)
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                    }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
        setListener()
    }

    private fun init() {
        mBlueToothAdapter = BluetoothAdapter.getDefaultAdapter()
        recPairedDevices.layoutManager = LinearLayoutManager(this)
        setInitialBluetoothInfo()

        mProgressDialog = ProgressDialog(this)

        //State change
        val filterStateChanged = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(receiverBluetoothStateChange, filterStateChanged)

        //Discover devices
        val filterActionFound = IntentFilter()
        filterActionFound.addAction(BluetoothDevice.ACTION_FOUND)
        filterActionFound.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filterActionFound.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filterActionFound.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

        registerReceiver(receiverDeviceDiscover, filterActionFound)

        if (mBlueToothAdapter.isEnabled)
            BluetoothServerController(this).start()
    }

    /**
     * Set initial bluetooth info
     */
    private fun setInitialBluetoothInfo() {
        if (mBlueToothAdapter.isEnabled) {
            changeBluetoothInfo(true)
        } else {
            changeBluetoothInfo(false)
        }
    }


    @WithPermissions(permissions = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE])
    private fun setListener() {
        btnBT.setOnClickListener {
            turnOnOffBluetooth(!isChecked)
        }

        btnGetPairedDevice.setOnClickListener {
            getPairedDevice()
        }

        btnDiscoverDevice.setOnClickListener {
            discoverBluetoothDevices()
        }
    }

    /**
     * Discover near by bluetooth devices
     */
    private fun discoverBluetoothDevices() {
        if (mBlueToothAdapter.isEnabled) {
            val discoverableIntent: Intent =
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
            startActivity(discoverableIntent)


            if (mBlueToothAdapter.isDiscovering) {
                mBlueToothAdapter.cancelDiscovery()
                discoveredDevices.clear()
            }
            mBlueToothAdapter.startDiscovery()
            setDeviceAdapter(discoveredDevices)
        } else {
            toastNow("Turn on bluetooth to discover devices")
        }
    }

    /**
     * Get paired bluetooth devices
     */
    private fun getPairedDevice() {
        if (mBlueToothAdapter.isEnabled) {
            val pairedDevices = mBlueToothAdapter.bondedDevices
            setDeviceAdapter(pairedDevices.toList())
        } else {
            toastNow("Turn on bluetooth to get paired devices")
        }
    }

    /**
     * Set paired devices into recycler view
     */
    private fun setDeviceAdapter(pairedDevices: List<BluetoothDevice>) {
        LastAdapter(pairedDevices, BR.item)
            .map<BluetoothDevice, ItemBluetoothDeviceBinding>(R.layout.item_bluetooth_device) {
                onBind {
                    val binding = it.binding
                    val currentItem = binding.item

                    when (currentItem?.bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            binding.btnPairUnpair.text = "Unpair"
                            binding.btnSendFile.show()
                        }

                        BluetoothDevice.BOND_NONE -> {
                            binding.btnPairUnpair.text = "Pair"
                            binding.btnSendFile.hide()
                        }

                        BluetoothDevice.BOND_BONDING -> {
                            binding.btnPairUnpair.text = "Pairing"
                            binding.btnSendFile.hide()
                        }
                    }

                    binding.btnPairUnpair.setOnClickListener {
                        if (currentItem?.bondState == BluetoothDevice.BOND_BONDED) {
                            try {
                                val method: Method = currentItem.javaClass.getMethod("removeBond")
                                method.invoke(currentItem)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (currentItem?.bondState == BluetoothDevice.BOND_NONE) {
                            try {
                                val method: Method =
                                    currentItem.javaClass.getMethod("createBond")
                                method.invoke(currentItem)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    binding.btnSendFile.setOnClickListener {
                        if (mBlueToothAdapter.isEnabled) {
                            mSelectedDevice = currentItem

                            TedImagePicker.with(this@MainActivity)
                                .start { uri -> onImageSelected(uri) }
                        } else {
                            toastNow("Turn on bluetooth")
                        }
                    }
                }
            }
            .into(recPairedDevices)
    }

    /**
     * Callback of image selection
     */
    private fun onImageSelected(uri: Uri) {
        progressBar.show()

        val bitmap =
            MediaStore.Images.Media.getBitmap(this.contentResolver, uri)


        val executor = Executors.newSingleThreadExecutor()
        executor.execute(ConvertImageToByte(bitmap))
        executor.execute(BluetoothClient(mSelectedDevice!!))
    }

    /**
     * Turn on/off bluetooth and manage UI
     */
    private fun turnOnOffBluetooth(isChecked: Boolean) {
        if (isChecked) {
            if (!mBlueToothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            if (mBlueToothAdapter.isEnabled) {
                mBlueToothAdapter.disable()
                changeBluetoothInfo(false)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (resultCode) {
            Activity.RESULT_OK -> {
                when (requestCode) {
                    REQUEST_ENABLE_BT -> {
                        changeBluetoothInfo(true)
                        BluetoothServerController(this).start()
                    }
                }
            }

            Activity.RESULT_CANCELED -> {
                when (requestCode) {
                    REQUEST_ENABLE_BT -> {
                        changeBluetoothInfo(false)
                    }
                }
            }
        }
    }

    /**
     * Change UI according to bluetooth status
     */
    private fun changeBluetoothInfo(isOn: Boolean) {
        if (isOn) {
            tvInfo.text = resources.getString(R.string.bluetooth_on)
            btnBT.text = resources.getString(R.string.on)
            isChecked = true
        } else {
            tvInfo.text = resources.getString(R.string.bluetooth_off)
            btnBT.text = resources.getString(R.string.off)
            isChecked = false
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiverDeviceDiscover)
        unregisterReceiver(receiverBluetoothStateChange)
        BluetoothServerController(this).cancel()
    }

    /**
     * Append text which get by reading bytes from bluetooth server
     */
    fun appendText(text: String) {
        runOnUiThread {
            tvMsg?.text = tvMsg?.text.toString() + "\n" + text
        }
    }


    /**
     * Accept the request of client and establish connection
     * @param activity
     */
    inner class BluetoothServerController(private val activity: MainActivity) : Thread() {
        private var cancelled: Boolean
        private val serverSocket: BluetoothServerSocket?

        init {
            if (mBlueToothAdapter != null) {
                this.serverSocket =
                    mBlueToothAdapter.listenUsingRfcommWithServiceRecord("test", uuid)
                this.cancelled = false
            } else {
                this.serverSocket = null
                this.cancelled = true
            }

        }

        override fun run() {
            var socket: BluetoothSocket

            while (true) {
                if (this.cancelled) {
                    break
                }

                try {
                    socket = serverSocket!!.accept()
                } catch (e: IOException) {
                    break
                }

                if (!this.cancelled && socket != null) {
                    Log.i("server", "Connecting")
                    BluetoothServer(this.activity, socket).start()
                }
            }
        }

        fun cancel() {
            this.cancelled = true
            this.serverSocket!!.close()
        }
    }

    /**
     * Reading the bytes coming from client and send it to handler to write in local file
     * @param activity
     * @param socket connected socket
     */
    class BluetoothServer(private val activity: MainActivity, private val socket: BluetoothSocket) :
        Thread() {
        private val inputStream = this.socket.inputStream
        private val outputStream = this.socket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            activity.destFile = activity.getFileToWriteBytes()
            activity.receivedStream = FileOutputStream(activity.destFile!!)

            var numBytes: Int = 0// bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            var readMsg: Message
            while (true) {
                // Read from the InputStream.
                try {
                    numBytes = inputStream.read(mmBuffer)

                    readMsg = activity.handler.obtainMessage(
                        MESSAGE_READ, numBytes, -1,
                        mmBuffer
                    )
                    readMsg.sendToTarget()
                } catch (e: IOException) {
                    Log.d("Server", "Input stream was disconnected", e)
                    readMsg = activity.handler.obtainMessage(
                        MESSAGE_TOAST, numBytes, -1,
                        mmBuffer
                    )
                    readMsg.sendToTarget()
                    break
                }
            }

            inputStream.close()
            outputStream.close()
            socket.close()


            //Use below code for string(small bytes)
//            try {
//                sleep(2000)
//                val available = inputStream.available()
//                val bytes = ByteArray(available)
//                val bytesToUse = ByteArray(available)
//
////                val read = inputStream.read()
//
//                Log.i("server", "Reading")

//                val text = String(bytes)
//                Log.i("server", "Message received")
////                Log.i("server", text)
//
//            } catch (e: java.lang.Exception) {
//                Log.e("server", "Cannot read data", e)
//                inputStream.close()
//                outputStream.close()
//                socket.close()
//            } finally {
//
//            }
        }


    }

    /**
     * Connect to the device to which want to transfer data
     * Write data in the form of bytes
     * @param device target device
     */
    inner class BluetoothClient(device: BluetoothDevice) : Thread() {
        private val socket = device.createRfcommSocketToServiceRecord(uuid)

        override fun run() {
            Log.i("client", "Connecting")
            this.socket.connect()

            Log.i("client", "Sending")
            val outputStream = this.socket.outputStream
            val inputStream = this.socket.inputStream
            try {
                var currentIndex = 0
                val CHUNK_SIZE = 200

                val size = mByteArray!!.size
                while (currentIndex < size) {
                    val currentLength = Math.min(size - currentIndex, CHUNK_SIZE)
                    outputStream.write(mByteArray!!, currentIndex, currentLength)
                    currentIndex += currentLength
                }

//                outputStream.write(mByteArray!!)
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                this.socket.close()
                Log.i("client", "Sent")
                runOnUiThread { toastNow("File sent successfully") }
            } catch (e: java.lang.Exception) {
                Log.e("client", "Cannot send", e)
                runOnUiThread { toastNow("File didn't send, Something went wrong") }
            } finally {
                runOnUiThread { this@MainActivity.progressBar.hide() }
            }
        }
    }


    /**
     * Convert selected image to byte array to send via bluetooth
     * @param bitmap Bitmap of selected image
     */
    inner class ConvertImageToByte(private val bitmap: Bitmap) : Thread() {
        private val stream = ByteArrayOutputStream()

        override fun run() {
            Log.i("convertingThread", "Start")
            try {
                this.bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                mByteArray = stream.toByteArray()
                Log.i("convertingThread", "finish")
            } catch (e: java.lang.Exception) {
                Log.e("convertingThread", "Conversion fail", e)
            } finally {
                stream.close()
            }
        }
    }

    /**
     * Create file to write coming byte
     * @return File Created file
     */
    private fun getFileToWriteBytes(): File {
        var dest: File? = null
        val currentTime = System.currentTimeMillis()
        val destPath = "/storage/emulated/0/bluetooth/images/"
        val destFileName = "image_$currentTime.png"
        val externalStoragePublicDirectory = File(destPath)

        if (if (!externalStoragePublicDirectory.exists()) externalStoragePublicDirectory.mkdir() else true) {
            dest = File(externalStoragePublicDirectory, destFileName)
        }
        return dest!!
    }
}
