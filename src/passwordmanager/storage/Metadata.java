package passwordmanager.storage;

import java.util.Arrays;
import passwordmanager.PasswordManager;

public class Metadata {
    private final String key; // The key (e.g., website name) associated with this metadata.
    private String[] data; // The array of credentials associated with the key.
    private boolean encrypted; // Flag to indicate whether the data is encrypted.

    public Metadata(String key, boolean encrypted, String... data) {
        this.key = key; // Assign the key
        this.encrypted = encrypted; // Set the initial encryption state
        this.data = data; // Store the data (credentials)
    }

    public void decrypt() {
        if (!encrypted) return; // If already decrypted, exit the method.

        // Decrypt each data entry using PasswordManager's Cyfer method.
        for (int i = 0; i < data.length; i++) {
            data[i] = PasswordManager.Cyfer(data[i]);
        }
        encrypted = false; // Mark the data as decrypted.
    }


    public void encrypt() {
        if (encrypted) return; // If already encrypted, exit the method.

        // Encrypt each data entry using PasswordManager's Cyfer method.
        for (int i = 0; i < data.length; i++) {
            data[i] = PasswordManager.Cyfer(data[i]);
        }
        encrypted = true; // Mark the data as encrypted.
    }

    public String getKey() {
        return key;
    }

    public String[] getData() {
        return data;
    }

    public void putData(String[] d) {
        String[] combined = new String[data.length + d.length];
        System.arraycopy(data, 0, combined, 0, data.length); // Copy existing data.
        System.arraycopy(d, 0, combined, data.length, d.length); // Append new data.
        this.data = combined; // Update the data array.
    }

    public void remove(String dat) {
        // Filter out the data entry that matches the given value and update the data array.
        data = Arrays.stream(data).filter(d -> !d.equals(dat))
                .toArray(String[]::new);
    }

    public boolean isEncrypted() {
        return encrypted;
    }
}