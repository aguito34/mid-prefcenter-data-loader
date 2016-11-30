package com.timeinc.rde.prefCenter;


import datomic.Connection;
import datomic.Peer;
import datomic.Util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by vanm on 6/30/16.
 */
public class Transactor {

    private static final int THREADS = 4;
    private static Connection conn;

    public static void main(String[] args) throws ParseException, ExecutionException, InterruptedException, IOException {
        String magCode = "GF";
        String version = "0930";
        String uriSelf = "datomic:dev://localhost:4334/testself";
        String fileNameWithPathSelf = "gf-0930-self-reported/gf_uniq_dt0930.csv.quads";
        String uriDemo = "datomic:dev://localhost:4334/testdemo";
        String fileNameWithPathDemo = "gf-0930-demographic/gf_uniq_dt0930.csv.quads";

        String uriSelfServer = "datomic:ddb://us-east-1/ti-use1b-rde-dynamo-preference-center-ask-james-ruska-at-timeinc/selfReported";
        String uriDemoServer = "datomic:ddb://us-east-1/ti-use1b-rde-dynamo-preference-center-ask-james-ruska-at-timeinc/demographic";

        boolean transactSelfReported = false;
        boolean transactLocal = false;
        boolean createDatabase = false;
        int startFile = 1, endFile = 1;

        Scanner in = new Scanner(System.in);

        if(args.length == 5) {
            magCode = args[0];
            fileNameWithPathSelf = args[1];
            startFile = Integer.parseInt(args[2]);
            endFile = Integer.parseInt(args[3]);
            createDatabase = Boolean.parseBoolean(args[4]);
        } else {
            System.out.println("Environment: local: (y/n)");
            String opt = in.next();
            if(opt.toLowerCase().compareTo("y")!=0){
                transactLocal = false;
//                System.out.println("Version(mmdd):");
//                in = new Scanner(System.in);
//                op = in.next();
//                if(opt.toLowerCase().compareTo("y")!=0){
//                    System.out.println("Please pass following arguments: [mag_code] [uri_self] [fileNameWithPath_self] [start_file] [end_file] [create_database_boolean]");
//                    System.exit(0);
//                }
            } else {
                transactLocal = true;
            }

            System.out.println("Version: ");
            version = in.next();
            System.out.println("You entered " + version + " is it correct? y/n:");
            opt = in.next();
            if(opt.toLowerCase().compareTo("y")!=0){
                System.out.println("Please start again");
                System.exit(0);
            }

            System.out.println("Query counts (1) or transact (2):");
            String option = in.next();
            if(option.compareTo("1") == 0){
                if(transactLocal) {
                    System.out.println("Querying local dbs...");
                    checkCount(uriSelf, uriDemo);
                }
                else {
                    checkCount(uriSelfServer, uriDemoServer);
                }
                System.exit(0);
            }
            else{
                System.out.println("Enter mag code:");
                magCode = in.next().trim();


                System.out.println("Create db: (y/n): ");
                String createDb = in.next();
                if(createDb.toLowerCase().compareTo("y") == 0){
                    createDatabase = true;
                }

                System.out.println("Database: self (1) or demographic (2): ");
                int db = in.nextInt();
                if(db == 1){
                    transactSelfReported = true;
                } else if(db == 2){
                    transactSelfReported = false;
                } else {
                    System.out.println("Please enter 1 or 2");
                    System.exit(0);
                }

                System.out.println("Start edn file: ");
                startFile = in.nextInt();
                System.out.println("End edn file: ");
                endFile = in.nextInt();
            }
        }

        if(transactLocal){
            if(transactSelfReported){
                if(createDatabase){
                    Peer.createDatabase(uriSelf);
                }
                conn = Peer.connect(uriSelf);
                if(createDatabase) {
                    loadSchema(conn);
                }
                long start = System.nanoTime();
                transactData(conn, fileNameWithPathSelf, magCode, startFile, endFile);
                System.out.println("Total time to transact data: " + ((System.nanoTime() - start)/1000000) + "ms.");
            }else {
                if(createDatabase){
                    Peer.createDatabase(uriDemo);
                }
                conn = Peer.connect(uriDemo);
                if(createDatabase){
                    loadSchemaDemographic(conn);
                }
                long start = System.nanoTime();
                transactData(conn, fileNameWithPathDemo, magCode, startFile, endFile);
                System.out.println("Total time to transact data: " + ((System.nanoTime() - start)/1000000) + "ms.");
            }
        }else{
            String uri;
            String fileNameWithPath;
            if(transactSelfReported){
                uri = uriSelfServer;
                fileNameWithPath = magCode.toLowerCase() + "-" + version +"-self-reported/" + magCode.toLowerCase()+ "_uniq_dt"+version+".csv.quads";
            } else {
                uri = uriDemoServer;
                fileNameWithPath = magCode.toLowerCase() + "-"+ version +"-demographic/" + magCode.toLowerCase()+ "_uniq_dt"+version+".csv.quads";
            }
            System.out.println("File name: " + fileNameWithPath + " start " + startFile + " endFile " + endFile);
            verify(in);

            if(createDatabase){
                Peer.createDatabase(uri);
            }
            conn = connectToDatabase(uri);
            if(createDatabase){
                if(transactSelfReported){
                    loadSchema(conn);
                }else{
                    loadSchemaDemographic(conn);
                }
            }
            System.out.println("Transact data...");
            long start = System.nanoTime();
            transactData(conn, fileNameWithPath, magCode, startFile, endFile);
            System.out.println("Total time to transact data: " + ((System.nanoTime() - start)/1000000) + "ms.");

        }
        System.out.println("\nExit");

        System.exit(0);
    }

    private static void verify(Scanner in) {
        System.out.println("Is the path correct?");
        String correct = in.next();
        if(correct.toLowerCase().compareTo("y") != 0){
            System.out.println("Ok...begin again!");
            System.exit(0);
        }
    }

    public static Connection connectToDatabase(String uri) {
        return Peer.connect(uri);
    }

    public static void loadSchema(Connection conn) {
        try {
            transactFileSync(conn, "files/tx1-schema.edn");
            transactFileSync(conn, "files/tx2-entities.edn");
//            transactFileSync(conn, "files/tx3-EW-schema.edn");  //EW
//            transactFileSync(conn, "files/tx4-EW-entities.edn");  //EW
            transactFileSync(conn, "files/tx5-GF-schema.edn");
            transactFileSync(conn, "files/tx6-GF-entities.edn");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static void loadSchemaDemographic(Connection conn) {
        try {
            transactFileSync(conn, "files/tx1-schema-demographic.edn");
            transactFileSync(conn, "files/tx2-entities-demographic.edn");
//            transactFileSync(conn, "files/tx3-EW-schema-demographic.edn");   // EW
//            transactFileSync(conn, "files/tx4-EW-entities-demographic.edn");  // EW
            transactFileSync(conn, "files/tx5-GF-schema-demographic.edn");
            transactFileSync(conn, "files/tx6-GF-entities-demographic.edn");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void transactFileSync(Connection conn, String fileName) {
        try{
            File file = new File(fileName);
            Reader schema_rdr = new FileReader(file.getAbsolutePath());
            List schema_tx = (List) Util.readAll(schema_rdr).get(0);
            Object txResult = conn.transact(schema_tx).get();
            System.out.println("Finished transacting " + fileName);
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    public static void transactData(Connection conn, String fileNameWithPath, String magCode, int startFile, int endFile) throws InterruptedException, ExecutionException {
        ExecutorService exec = Executors.newFixedThreadPool(THREADS);
        List<AsyncTransact> futureList = new ArrayList<>();
        for(int i = startFile; i<= endFile; i++){
            String fileNumber = Integer.toString(i);
            AsyncTransact tx = new AsyncTransact(fileNameWithPath + fileNumber + ".edn");
            futureList.add(tx);
        }
        System.out.println("Start");
        try {
            List<Future<Boolean>> futures = exec.invokeAll(futureList);
            for(Future<Boolean> future : futures){
                try{
                    System.out.println("future.isDone = " + future.isDone());
                } catch(Exception e1){
                    e1.printStackTrace();
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        exec.shutdown();

        conn.release();
    }

    public static void checkCount(String uriSelf, String uriDemo) {
        Connection connSelf = Peer.connect(uriSelf);
        Connection connDemo = Peer.connect(uriDemo);

        String customerQuery = "[:find ?c :where [ ?c :customer/acctid _]]";
        Collection results = Peer.query(customerQuery, connSelf.db());
        System.out.println("Customers  in SELF: " + results.size());
        String logQuery = "[:find ?c :where [ ?c :log/acctid _]]";
        results = Peer.query(logQuery, connSelf.db());
        System.out.println("Logs  in SELF: " + results.size());
        results = Peer.query(logQuery, connDemo.db());
        System.out.println("Logs  in DEMO: " + results.size());

        connDemo.release();
        connSelf.release();
    }


    static class AsyncTransact implements Callable<Boolean>{
        private String fileName;

        public AsyncTransact(String fileName){
            this.fileName = fileName;
        }
        public Boolean call() throws InterruptedException, ExecutionException, IOException {
            Reader data_rdr = new FileReader(fileName);
            List data_tx = (List) Util.readAll(data_rdr).get(0);
            data_rdr.close();
            Object txResult = conn.transactAsync(data_tx).get();
            return (boolean) txResult;
        }
    }
}
