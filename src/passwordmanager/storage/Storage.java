package passwordmanager.storage;

import java.io.*;
import java.nio.file.Path;
import java.util.*;


public class Storage {
    private Map<String,Metadata> data = new HashMap<>();
    private File file;

    public Storage(String username) {
        // Get storage file based of username
        file = Path.of("")
                .toAbsolutePath()
                .resolve("data/" + username + ".encrypt")
                .toFile();
        
        // Check if the file exists. If not, create a new file and its parent directories.
        if (!file.exists()) {
            file.getParentFile().mkdir();
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // If the file exists, read its content to populate the data map.
            try (FileReader reader = new FileReader(file)) {
                State STATE = State.KEY; // Initial state is to read the key.
                StringBuilder currentKey = new StringBuilder(); // To store the current key being read.
                StringBuilder currentValue = new StringBuilder(); // To store the current value being read.
                List<String> values = new ArrayList<>(); // To collect values associated with a key.
                short currentLength = 0; // Length of the current value being processed.
                short pos = 0; // Position in the current value being processed.
                short byte2;

                // Read the file character by character.
                while ((byte2 = (short) reader.read()) != -1) {
                    char record = (char) byte2; // Convert the byte to a character.
                    
                    // Switch between different states: reading key, value, or size
                    switch (STATE) {
                        case KEY -> {
                            if (record == '#') {
                                // Transition to SIZE state when '#' is encountered
                                STATE = State.SIZE;
                                continue;
                            }
                            currentKey.append(record);
                        }
                        case VALUE -> {
                            if (pos == currentLength) {
                                values.add(currentValue.toString());
                                // Reset the current value builder.
                                currentValue = new StringBuilder();
                                
                                if (record == '#') {
                                    STATE = State.SIZE; // Transition to SIZE state.
                                } else {
                                    STATE = State.KEY; // Transition back to KEY state
                                    String key = currentKey.toString();
                                    // Reset the current key builder.
                                    currentKey = new StringBuilder();
                                    // Store the key-value pair in the data map.
                                    data.put(key, new Metadata(key, true, values.toArray(String[]::new)));
                                    values.clear();
                                    currentKey.append(record);
                                }
                                pos = 0; // Reset the position counter.
                            } else {
                                currentValue.append(record); // Append character to the current value
                                pos++;
                            }
                        }
                        case SIZE -> {
                            currentLength = byte2; // Set the current value length.
                            STATE = State.VALUE; // Transition to VALUE state
                        }
                    }
                }
                
                // After reading the file, handle any remaining key and value.
                String key = currentKey.toString();
                if (key.isEmpty()) return; // return if no key exists
                // Add the last value.
                values.add(currentValue.toString());
                // Store the final key-value pair in the data map.
                data.put(key, new Metadata(key, true, values.toArray(String[]::new)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public void remove(String key, String dat) {
        Metadata meta = data.get(key); // Fetch the metadata associated with the key
        if (meta == null) return; // return if key isn't valid
        
        if (meta.getData().length == 1) {
            // If there's only one value, remove the key entirely
            data.remove(key);
            return;
        }
        // Remove the specified value from the metadata
        meta.remove(dat); 
    }

    public Metadata fetch(String key){
        return data.get(key);
    }

    public Metadata[] fetchAll(){
        return data.values().toArray(Metadata[]::new);
    }

    public void put(Metadata meta) {
        Metadata m = data.get(meta.getKey()); // Check if the key already exists.
        if (m == null) {
            data.put(meta.getKey(), meta); // add key if there is no key already.
        } else {
            m.decrypt(); // Decrypt existing data.
            m.putData(meta.getData()); // Merge the new data with existing data.
        }
    }

    public boolean save() {
        try {
            FileWriter writer = new FileWriter(file); // Open a writer to the file.
            
            for (Metadata meta : data.values()) {
                // Encrypt the data before saving.
                meta.encrypt();
                // Write the key.
                writer.append(meta.getKey());
                
                for (String value : meta.getData()) {
                    // Write the value length and data.
                    writer.append("#").append((char) value.length());
                    writer.append(value);
                }
            }
            // Flush the writer to ensure all data is written.
            writer.flush();
            writer.close();
            return true; // Return true if saving was successful.
        } catch (IOException e) {
            return false; // Return false if an error occurred during saving.
        }
    }

    private enum State {
        KEY,
        VALUE,
        SIZE
    }
}