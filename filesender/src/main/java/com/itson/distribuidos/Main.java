package com.itson.distribuidos;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        Client client = new Client();
        try {
            client.askForFiles("localhost", 8080);
        } catch (Exception e) {
            e.printStackTrace();
        }


        // ExecutorService executor = Executors.newSingleThreadExecutor();
        // ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        // Callable<String> tareaPrincipal = () -> {
        //     // Simulamos una tarea que puede tardar más de 5 segundos
        //     System.out.println("Tarea principal iniciada");
        //     Thread.sleep(4000); // Simula una tarea larga (7 segundos)
        //     return "Tarea principal completada";
        // };
        // System.out.println("1");
        // Future<String> future = executor.submit(tareaPrincipal);
        // System.out.println("2");
        // // Programamos la tarea de respaldo para ejecutarse después de 5 segundos
        // scheduler.schedule(() -> {
        //     System.out.println("Tarea de respaldo iniciada");
        //     if (!future.isDone()) {
        //         System.out.println("Tarea principal no respondió a tiempo. Ejecutando tarea de respaldo.");
        //         future.cancel(true); // Cancela la tarea principal si sigue en ejecución
        //     }
        // }, 5, TimeUnit.SECONDS);
        // System.out.println("3");
        // try {
        //     // Esperamos el resultado de la tarea principal con un límite de 5 segundos
        //     String resultado = future.get(5, TimeUnit.SECONDS);
        //     System.out.println(resultado);
        // } catch (TimeoutException e) {
        //     System.out.println("Tiempo de espera agotado.");
        // } catch (InterruptedException | ExecutionException e) {
        //     System.out.println("Error en la ejecución de la tarea.");
        // } finally {
        //     executor.shutdown();
        //     scheduler.shutdown();
        // }

        // Random random = new Random();
        // String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        // StringBuilder builder = new StringBuilder();
        // for (int i = 0; i < 6; i++) {
        //     int index = random.nextInt(ALPHANUMERIC.length());
        //     builder.append(ALPHANUMERIC.charAt(index));
        // }
        // System.out.println(builder.toString());
        // builder = new StringBuilder();
        // for (int i = 0; i < 6; i++) {
        //     int index = random.nextInt(ALPHANUMERIC.length());
        //     builder.append(ALPHANUMERIC.charAt(index));
        // }
        // System.out.println(builder.toString());
        // builder = new StringBuilder();
        // for (int i = 0; i < 6; i++) {
        //     int index = random.nextInt(ALPHANUMERIC.length());
        //     builder.append(ALPHANUMERIC.charAt(index));
        // }
        // System.out.println(builder.toString());
    }
}