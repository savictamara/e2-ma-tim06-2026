package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class ChatMessage {
    public String id;
    public String senderId;
    public String senderName;
    public String region;
    public String text;
    public Timestamp timestamp;

    public ChatMessage() {
    }

    public static ChatMessage fromSnapshot(DocumentSnapshot doc) {
        ChatMessage message = new ChatMessage();
        message.id = doc.getId();
        message.senderId = value(doc.getString("senderId"));
        message.senderName = value(doc.getString("senderName"));
        message.region = value(doc.getString("region"));
        message.text = value(doc.getString("text"));
        message.timestamp = doc.getTimestamp("timestamp");
        return message;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
