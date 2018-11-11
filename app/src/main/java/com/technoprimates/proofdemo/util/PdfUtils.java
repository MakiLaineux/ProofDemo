package com.technoprimates.proofdemo.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

// PDF utility methods
public class PdfUtils {

    // Create proof file (pdf form)
    public static boolean createPdfProofFile(Context context, String displayName, String proofString, int requestNumber){
        String fileNameWithoutExtsension;
        String newXmpMetadata, oldXmpMetadata;
        File sourceFile = new File(context.getFilesDir(), String.format(Locale.US, "%04d", requestNumber));

        try {
        // Copy the saved file in external storage with proof filename
        // Strip Filename's extension if exists
        if (displayName.lastIndexOf(".pdf") == displayName.length()-4){ // filename ends with ".pdf"
            fileNameWithoutExtsension = displayName.substring(0, displayName.length()-4);
        } else {
            fileNameWithoutExtsension = displayName;
        }
        String outName =Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL +
                fileNameWithoutExtsension + String.format(Locale.US, ".%04d", requestNumber)+ ".pdf" ;
        FileChannel in = new FileInputStream(sourceFile).getChannel();
        FileChannel out = new FileOutputStream(outName).getChannel();
        final long size = in.size();
        long position = 0;
        while (position < size) {
            position += in.transferTo(position, 1024 * 1024, out);
        }
        in.close();
        out.close();

        // Change the metadata
        // Get target pdf file just created
        PDFBoxResourceLoader.init(context);
        File targetFile = new File(outName);
        PDDocument document = PDDocument.load(targetFile);

        // Get metadata, it should exist
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDMetadata metadata = catalog.getMetadata();
        if (metadata == null) {
            Log.e(Constants.TAG, "ERROR no metadata to overwrite");
            return false;
        }
        InputStream is = metadata.createInputStream();
        byte[] bufMetadata = new byte[16000]; // it seems enough
        int i = is.read(bufMetadata);
        if (i > 0) {
            oldXmpMetadata = new String(bufMetadata, 0, i, "UTF-8"); // metadata string
        } else {
            oldXmpMetadata = null;
        }

        //build new metadata with actual proof data
        newXmpMetadata = XmpUtils.buildXmpProofMetadata(oldXmpMetadata, proofString);

        // Now store the new metadata and save the document
        Log.d(Constants.TAG, "----  METADATA READY ------    "+ newXmpMetadata);
        ByteArrayInputStream mdInput = new ByteArrayInputStream ( newXmpMetadata.getBytes() );
        metadata = new PDMetadata(document, mdInput );
        catalog.setMetadata( metadata );
        document.save(targetFile);
        document.close();
        return true;
    }

    catch (IOException e) {
        Log.e(Constants.TAG, "IO Exception while creating proof file (pdf form) : " + e);
        e.printStackTrace();
        return false;
        }
    }

    //Extract proof from prd file
    protected static String readProofFromFullUri(Context context, Uri fullUri){

        try {
            // Get PDF document
        InputStream in = context.getContentResolver().openInputStream(fullUri);
        PDDocument document = PDDocument.load(in);

        // Get metadata, it should exist
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDMetadata metadata = catalog.getMetadata();
        if (metadata == null) {
            Log.e(Constants.TAG, "ERROR no metadata found in proof file");
            return null;
        }
        // Get proof metadata, it should be present
        InputStream is = metadata.createInputStream();
        byte[] bufMetadata = new byte[16000]; // it seems enough
        int i = is.read(bufMetadata);
        if (i<=0){
            Log.e(Constants.TAG, "ERROR no metadata found in proof file (empty)");
            return null;
        }
        String xmpMetadata = new String(bufMetadata, 0, i, "UTF-8"); // metadata string
        return XmpUtils.getProofStringFromXmpMetadata(xmpMetadata);
        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "read proof from filename : File not found");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            Log.e(Constants.TAG, "read proof from filename : IO Exception");
            e.printStackTrace();
            return null;
        }

    }

    // For PDF Variant : insert or replace a neutral proof metadata of 4000 bytes
    protected static boolean addProofNeutralMetadata (Context context, String fileName){

        try {
            String newXmpMetadata, oldXmpMetadata;

            // Get pdf file
            PDFBoxResourceLoader.init(context);
            File sourceFile = new File(context.getFilesDir(), fileName);
            PDDocument document = PDDocument.load(sourceFile);

            // Get metadata if exists
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDMetadata metadata = catalog.getMetadata();
            if (metadata != null) {
                InputStream is = metadata.createInputStream();
                byte[] bufMetadata = new byte[16000]; // it seems enough
                int i = is.read(bufMetadata);
                if (i > 0) {
                    oldXmpMetadata = new String(bufMetadata, 0, i, "UTF-8"); // metadata string
                } else {
                    oldXmpMetadata = null;
                }
            } else {
                oldXmpMetadata = null;
            }

            //build new metadata with null proof string
            newXmpMetadata = XmpUtils.buildXmpProofMetadata(oldXmpMetadata, null); // null proofstring means neutral proof

            // Now store the new metadata and save the document
            Log.d(Constants.TAG, "----  METADATA READY ------    "+ newXmpMetadata);
            ByteArrayInputStream mdInput = new ByteArrayInputStream ( newXmpMetadata.getBytes() );
            metadata = new PDMetadata(document, mdInput );
            catalog.setMetadata( metadata );
            document.save(sourceFile);
            document.close();
            return true;

        } catch (IOException e) {
            Log.e(Constants.TAG, "Add neutral proof failed : " + e);
            e.printStackTrace();
            return false;
        }
    }

    //Extract ready-to-hash file from proof file
    protected static boolean saveFileToHash(Context context,  String pdfName, String tmpString){
        String newXmpMetadata;
        String oldXmpMetadata;
        byte[] bytes = new byte[4000];
        Arrays.fill(bytes, (byte)' ');

        try {
            File tmpFile = new File(context.getFilesDir(), "tmpfile");
            FileOutputStream fout = new FileOutputStream(tmpFile);

            // Copy the file
            FileChannel in = new FileInputStream(Environment.getExternalStorageDirectory()+
                    Constants.DIRECTORY_LOCAL+pdfName).getChannel();
            FileChannel out = fout.getChannel();
            final long size = in.size();
            long position = 0;
            while (position < size) {
                position += in.transferTo(position, 1024 * 1024, out);
            }
            in.close();
            out.close();

            // Change the metadata to empty proof metadata
            // Get the tmp pdf file just created
            PDFBoxResourceLoader.init(context);
            PDDocument document = PDDocument.load(tmpFile);

            // Get metadata, it should exist
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDMetadata metadata = catalog.getMetadata();
            if (metadata == null) {
                Log.e(Constants.TAG, "ERROR no metadata to overwrite");
                return false;
            }

            // Get proof metadata, it should be present
            InputStream is = metadata.createInputStream();
            byte[] bufMetadata = new byte[16000]; // it seems enough
            int i = is.read(bufMetadata);
            if (i<=0){
                Log.e(Constants.TAG, "ERROR no metadata to overwrite (empty)");
                return false;
            }

            oldXmpMetadata = new String(bufMetadata, 0, i, "UTF-8"); // metadata string
            newXmpMetadata = XmpUtils.buildXmpProofMetadata(oldXmpMetadata, null); // xmp with empty formatted proof

            // Now store the new metadata
            Log.d(Constants.TAG, "----  METADATA READY ------    "+ newXmpMetadata);
            ByteArrayInputStream mdInput = new ByteArrayInputStream ( newXmpMetadata.getBytes() );
            metadata = new PDMetadata(document, mdInput );
            catalog.setMetadata( metadata );

            // Save the copied document
            document.save(tmpFile);
            document.close();
            return true;

        } catch (FileNotFoundException e) {
            Log.e(Constants.TAG, "saveFileToHash : file not found");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            Log.e(Constants.TAG, "saveFileToHash : IO Exception");
            e.printStackTrace();
            return false;
        }
    }
}
