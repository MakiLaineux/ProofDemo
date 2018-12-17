package com.technoprimates.proofdemo.struct;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import static com.technoprimates.proofdemo.util.Constants.*;
import com.technoprimates.proofdemo.util.ProofError;
import com.technoprimates.proofdemo.util.ProofException;
import com.technoprimates.proofdemo.util.XmpUtils;
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
import java.util.Arrays;

// PDF utility methods
public class PdfStampFile extends StampFile {

    public PdfStampFile(Context context, Uri uri) {
        super(context, uri);
    }

    public PdfStampFile(Context context, int dbId, String fileName, int fileType) {
        super(context, dbId, fileName, fileType);
    }

    /**
     * Writes a Stamped File in the app's output directory on External Storage
     * @param statement Statement string to add in the stamped file
     * @throws ProofException if write failed
     */
    public void write(String statement) throws ProofException{
        String fileNameWithoutExtsension;
        String newXmpMetadata, oldXmpMetadata;
        File sourceFile = new File(mContext.getFilesDir(), mDraftName);

        try {
            // Copy the saved file in external storage with proof filename
            // Strip Filename's extension if exists
            if (mFileName.lastIndexOf(".pdf") == mFileName.length()-4){ // filename ends with ".pdf"
                fileNameWithoutExtsension = mFileName.substring(0, mFileName.length()-4);
            } else {
                fileNameWithoutExtsension = mFileName;
            }
            String outName =Environment.getExternalStorageDirectory() + DIRECTORY_LOCAL +
                    fileNameWithoutExtsension + mDraftName + ".pdf" ;
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
            newXmpMetadata = XmpUtils.buildXmpProofMetadata(oldXmpMetadata, statement);

            // Now store the new metadata and save the document
            Log.d(TAG, "----  METADATA READY ------    "+ newXmpMetadata);
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

    /**
     * Gets the statement stored in a file.
     * @return The statement stored in the file or null if no statement was found
     * @throws ProofException if failed to find or open zip file
     */
    public String getStatementString()  throws ProofException {

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

    /**
     * get the Proof file type
     *
     * @return VARIANT_PDF
     */
    @Override
    public int typeOf() {
        return VARIANT_PDF;
    }

    /**
     * Saves the file's content to a draft file in the app's private storage. This draft
     * file will later be used to write the stamped file when the proof data will be received from the server
     * @throws ProofException
     */
    public void writeDraft(String draftName, boolean originalFile)  throws ProofException {
        String newXmpMetadata;
        String oldXmpMetadata;
        byte[] bytes = new byte[4000];
        Arrays.fill(bytes, (byte)' ');

        try {
            mDraftName = draftName;
            File tmpFile = new File(mContext.getFilesDir(), mDraftName);

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

            // Create metadata with empty proof (replace actual proof metadata if exists)
            // Get the tmp pdf file just created
            PDFBoxResourceLoader.init(mContext);
            PDDocument document = PDDocument.load(tmpFile);

            // Get metadata, if exists
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
            newXmpMetadata = XmpUtils.buildXmpProofMetadata(oldXmpMetadata, null); // xmp with empty formatted proof

            // Now store the new metadata
            Log.d(TAG, "----  METADATA READY ------    "+ newXmpMetadata);
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
