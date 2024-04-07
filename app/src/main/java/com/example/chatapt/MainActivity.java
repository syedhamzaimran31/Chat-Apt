package com.example.chatapt;

import static android.widget.Toast.LENGTH_LONG;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView welcomeTextView;
    EditText messageEditText;
    ImageButton sendButton;
    List<Message> messageList;

    MessageAdapter messageAdapter;

    public static final MediaType JSON = MediaType.get("application/json");
    String OPENAI_API_KEY = ""; // Replace this with your OpenAI API key

    OkHttpClient client = new OkHttpClient();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        messageList = new ArrayList<>();

        recyclerView = (RecyclerView) findViewById(R.id.recView);
        welcomeTextView = (TextView) findViewById(R.id.welcomeText);
        messageEditText = (EditText) findViewById(R.id.msg_edit_txt);
        sendButton = (ImageButton) findViewById(R.id.sendbtn);

        //rec View
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        sendButton.setOnClickListener(view -> {
            String question = messageEditText.getText().toString().trim();
            Toast.makeText(this, question, LENGTH_LONG).show();
            addToChat(question, Message.SENT_BY_ME);
            messageEditText.setText("");
            callApi(question,3,1000);
            welcomeTextView.setVisibility(View.GONE);
        });

    }


    void addToChat(String message, String sentBy) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageList.add(new Message(message, sentBy));
                messageAdapter.notifyDataSetChanged();
                recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
            }
        });
    }

    void addResponse(String response) {

        messageList.remove(messageList.size() - 1);
        addToChat(response, Message.SENT_BY_BOT);

    }

    void callApi(String question, int retryCount, long initialDelayMs) {
        if (retryCount <= 0) {
            addResponse("Exceeded maximum retry attempts.");
            return;
        }

        // Calculate the next retry delay with exponential backoff and jitter

        messageList.add(new Message("Typing... ", Message.SENT_BY_BOT));

        JSONObject jsonBody = new JSONObject();

        try {
            jsonBody.put("model", "gpt-3.5-turbo");
            jsonBody.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", question)));
            jsonBody.put("temperature", 0.7);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        RequestBody requestBody = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/completions")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + OPENAI_API_KEY)
                .post(requestBody)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                addResponse("Failed to load response due to " + e.getMessage());
                Toast.makeText(getApplicationContext(), "Failed on onFailure", Toast.LENGTH_LONG).show();
                // Retry with exponential backoff and jitter
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONArray choicesArray = jsonObject.getJSONArray("choices");
                        JSONObject choiceObject = choicesArray.getJSONObject(0);
                        JSONObject messageObject = choiceObject.getJSONObject("message");
                        String result = messageObject.getString("content");

                        runOnUiThread(() -> {
                            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
                            addResponse(result.trim());
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }

                } else {
                    addResponse("Failed to load response");
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), "Failed to load response", Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }
}