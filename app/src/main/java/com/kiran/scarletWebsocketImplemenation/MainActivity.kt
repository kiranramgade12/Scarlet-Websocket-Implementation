package com.kiran.scarletWebsocketImplemenation

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kiran.scarletWebsocketImplemenation.model.Message
import com.kiran.scarletWebsocketImplemenation.services.EchoService
import com.google.android.material.snackbar.Snackbar
import com.kiran.scarletWebsocketImplemenation.databinding.ActivityMainBinding
import com.tinder.scarlet.*
import com.tinder.scarlet.Message.Bytes
import com.tinder.scarlet.Message.Text
import com.tinder.scarlet.WebSocket.Event.*
import com.tinder.scarlet.lifecycle.android.AndroidLifecycle
import com.tinder.scarlet.streamadapter.rxjava2.RxJava2StreamAdapterFactory
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import io.reactivex.android.schedulers.AndroidSchedulers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.tinder.scarlet.Message as MessageScarlet


class MainActivity : AppCompatActivity() {

    companion object {
        private const val ECHO_URL = "wss://0nj6eimy16.execute-api.eu-central-1.amazonaws.com/test/?jwtToken=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwczovL2FwaS1tYi50ZXN0Lm1lZXRpbmctYm94LmNvbS9hcGkvdjEvb2F1dGgvdG9rZW4iLCJpYXQiOjE3MDYxMDEzMzksImV4cCI6MTcwNjEwNDkzOSwibmJmIjoxNzA2MTAxMzM5LCJqdGkiOiJ2QzNuZFhWTzJRTXVBWnU0Iiwic3ViIjoiMjAxMzQ3IiwicHJ2IjoiYzhlZTFmYzg5ZTc3NWVjNGM3Mzg2NjdlNWJlMTdhNTkwYjZkNDBmYyIsIm1vZGVsIjoiQXBwXFxNb2RlbHNcXEFjY291bnQiLCJwcm92aWRlciI6ImFjY291bnRzIiwiZGF0YWJhc2UiOiJ0ZXN0X2VtIiwidXNlcl9pZCI6MjAxMzQ3LCJldmVudF9pZCI6NjUwOTI1LCJ1c2VybmFtZSI6ImFha2FzaC52ZW5rYXRhc3VicmFtYW5pYW5Ac3VjY2V2by5jb20iLCJsYW5nIjpudWxsLCJldmVudF9jb2RlIjoiVVJQVERNIn0.M7r2yNzE3pbqxLyCLkgB5R8g2qCXq2I9UI4WRRSBoZM"
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var webSocketService: EchoService

    private lateinit var adapter: ChatMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupToolbar()
        setupEditTextMessage()
        setupRecyclerViewMessage()
        setupButtonSend(binding.etMessage.text.toString())

        setupWebSocketService()

        observeConnection()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = "connecting.."
    }

    private fun setupRecyclerViewMessage() {
        adapter = ChatMessageAdapter()
        binding.recyclerMessage.apply {
            adapter = this@MainActivity.adapter
            itemAnimator = DefaultItemAnimator()
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.VERTICAL, false)
        }
    }

    private fun setupWebSocketService() {
        webSocketService = provideWebSocketService(
            scarlet = provideScarlet(
                client = provideOkhttp(),
                lifecycle = provideLifeCycle(),
                streamAdapterFactory = provideStreamAdapterFactory(),
            )
        )
    }

    @SuppressLint("CheckResult")
    private fun observeConnection() {
        webSocketService.observeConnection()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                Log.d("observeConnection", response.toString())
                onReceiveResponseConnection(response)
            }, { error ->
                Log.e("observeConnection", error.message.orEmpty())
                Snackbar.make(binding.root, error.message.orEmpty(), Snackbar.LENGTH_SHORT).show()
            })
    }

    private fun onReceiveResponseConnection(response: WebSocket.Event) {
        when (response) {
            is OnConnectionOpened<*> -> changeToolbarTitle("connection opened")
            is OnConnectionClosed -> changeToolbarTitle("connection closed")
            is OnConnectionClosing -> changeToolbarTitle("closing connection..")
            is OnConnectionFailed -> changeToolbarTitle("connection failed")
            is OnMessageReceived -> handleOnMessageReceived(response.message)
        }
    }

    private fun handleOnMessageReceived(message: MessageScarlet) {
        adapter.addItem(Message(message.toValue(), false))
        binding.etMessage.setText("")
    }

    private fun MessageScarlet.toValue(): String {
        return when (this) {
            is Text -> value
            is Bytes -> value.toString()
        }
    }

    private fun changeToolbarTitle(title: String) {
        binding.toolbar.title = title
    }

    private fun setupEditTextMessage() {
        binding.etMessage.doAfterTextChanged {
            setupButtonSend("{\"action\":\"ping\"}")
        }
    }

    private fun setupButtonSend(message: String) {
        binding.btnSend.isEnabled = message.isNotBlank()
        binding.btnSend.setOnClickListener { sendMessage(message) }
    }

    private fun sendMessage(message: String) {
        webSocketService.sendMessage(message)
        adapter.addItem(Message(message = message, isFromSender = true))
    }

    private fun provideWebSocketService(scarlet: Scarlet) = scarlet.create(EchoService::class.java)

    private fun provideScarlet(
        client: OkHttpClient,
        lifecycle: Lifecycle,
        streamAdapterFactory: StreamAdapter.Factory,
    ) =
        Scarlet.Builder()
            .webSocketFactory(client.newWebSocketFactory(ECHO_URL))
            .lifecycle(lifecycle)
            .addStreamAdapterFactory(streamAdapterFactory)
            .build()

    private fun provideOkhttp() =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .build()

    private fun provideLifeCycle() = AndroidLifecycle.ofApplicationForeground(application)

    private fun provideStreamAdapterFactory() = RxJava2StreamAdapterFactory()
}