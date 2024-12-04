package passwordmanager.tasks;

import java.util.ArrayList;


public class AsyncTask {
    private static ArrayList<String> tasks = new ArrayList<>();
    private final String id;
    
    public AsyncTask(String id, Runnable task){
        this.id = id;
        // check if task is still busy
        if (!finnished()) return;
        // execute async task
        new Thread(() -> {
            // execute the runnable
            task.run();
            // after completion remove itself from the list
            remove();
        }).start();
        // add itself to the list
        tasks.add(id);
    }
    public AsyncTask(String id){
        this.id = id;
    }
    
    private synchronized void remove(){
        tasks.remove(id);
    }
    
    public synchronized boolean finnished(){
        return !tasks.contains(id);
    }
}
