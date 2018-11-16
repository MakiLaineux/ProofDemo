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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

// PDF utility methods
public class PdfProofFile extends ProofFile {

    PdfProofFile(Context context, Uri uri) {
        this.mUri = uri;
        this.mContext = context;
    }

    @Override
    protected int typeOf() {
        return Constants.VARIANT_PDF;
    }

    // Create proof file (pdf form)
    public void writeOnSDCard(String displayName, String proofString, int extensionPrefix) throws ProofException{
        String fileNameWithoutExtsension;
        String newXmpMetadata, oldXmpMetadata;
        File sourceFile = new File(mContext.getFilesDir(), String.format(Locale.US, "%04d", extensionPrefix));

        try {
            // Copy the saved file in external storage with proof filename
            // Strip Filename's extension if exists
            if (displayName.lastIndexOf(".pdf") == displayName.length()-4){ // filename ends with ".pdf"
                fileNameWithoutExtsension = displayName.substring(0, displayName.length()-4);
            } else {
                fileNameWithoutExtsension = displayName;
            }
            String outName =Environment.getExternalStorageDirectory() + Constants.DIRECTORY_LOCAL +
                    fileNameWithoutExtsension + String.format(Locale.US, ".%04d", extensionPrefix)+ ".pdf" ;
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
            PDFBoxResourceLoader.init(mContext);
            File targetFile = new File(outName);
            PDDocument document = PDDocument.load(targetFile);

            // Get metadata, it should exist
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDMetadata metadata = catalog.getMetadata();
            if (metadata == null) {
                throw new ProofException(ProofError.ERROR_NO_METADATA);
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
            return;
        }
        catch (IOException e) {
            throw new ProofException(ProofError.ERROR_PDFFILE_IO_ERROR);
        }
    }

    //Extract proof from prd file
    protected String readProof()  throws ProofException {

        try {
            // Get PDF document
        InputStream in = mContext.getContentResolver().openInputStream(mUri);
        PDDocument document = PDDocument.load(in);

        // Get metadata, it should exist
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDMetadata metadata = catalog.getMetadata();
        if (metadata == null) {
            throw new ProofException(ProofError.ERROR_NO_METADATA);
        }
        // Get proof metadata, it should be present
        InputStream is = metadata.createInputStream();
        byte[] bufMetadata = new byte[16000]; // it seems enough
        int i = is.read(bufMetadata);
        if (i<=0){
            throw new ProofException(ProofError.ERROR_NO_METADATA);
        }
        String xmpMetadata = new String(bufMetadata, 0, i, "UTF-8"); // metadata string
        return XmpUtils.getProofStringFromXmpMetadata(xmpMetadata);
        } catch (FileNotFoundException e) {
            throw new ProofException(ProofError.ERROR_PDFFILE_NOT_FOUND);
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_PDFFILE_IO_ERROR);
        }

    }

    // For PDF Variant : insert or replace a neutral proof metadata of 4000 bytes
    protected void addProofNeutralMetadata (String fileName)  throws ProofException {

        try {
            String newXmpMetadata, oldXmpMetadata;

            // Get pdf file
            PDFBoxResourceLoader.init(mContext);
            File sourceFile = new File(mContext.getFilesDir(), fileName);
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
            return;

        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_PDFFILE_IO_ERROR);
        }
    }

    //Extract ready-to-hash file from proof file
    protected void saveFileToHash()  throws ProofException {
        String newXmpMetadata;
        String oldXmpMetadata;
        byte[] bytes = new byte[4000];
        Arrays.fill(bytes, (byte)' ');

        try {
            File tmpFile = new File(mContext.getFilesDir(), "tmpfile");

            // Copy the file
            InputStream in = mContext.getContentResolver().openInputStream(mUri);
            FileOutputStream fout = new FileOutputStream(tmpFile);
            byte buffer[] = new byte[1024];
            int length;
            while((length=in.read(buffer)) > 0) {
                fout.write(buffer,0,length);
            }
            fout.close();
            in.close();

            // Change the metadata to empty proof metadata
            // Get the tmp pdf file just created
            PDFBoxResourceLoader.init(mContext);
            PDDocument document = PDDocument.load(tmpFile);

            // Get metadata, it should exist
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDMetadata metadata = catalog.getMetadata();
            if (metadata == null) {
                throw new ProofException(ProofError.ERROR_NO_METADATA);
            }

            // Get proof metadata, it should be present
            InputStream is = metadata.createInputStream();
            byte[] bufMetadata = new byte[16000]; // it seems enough
            int i = is.read(bufMetadata);
            if (i<=0){
                throw new ProofException(ProofError.ERROR_NO_METADATA);
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
            return ;

        } catch (FileNotFoundException e) {
            throw new ProofException(ProofError.ERROR_PDFFILE_NOT_FOUND);
        } catch (IOException e) {
            throw new ProofException(ProofError.ERROR_PDFFILE_IO_ERROR);
        }
    }
}
