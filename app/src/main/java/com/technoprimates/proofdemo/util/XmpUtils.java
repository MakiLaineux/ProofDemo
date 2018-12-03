package com.technoprimates.proofdemo.util;

import android.support.annotation.Nullable;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.common.PDMetadata;

import org.json.JSONObject;
import org.spongycastle.asn1.cms.MetaData;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Xmp handling methods
public class XmpUtils {

    // This method takes an existing xmp structure and returns this xmp including proof tags for given proof string
    // if input xmp is null or empty, then output xmp is built from scratch
    // if input xmp is not null without proof tags, return this xmp augmented with proof tags
    // if input xmp is not null and contains proof tags, replace the content between proof tags
    // Anyway the content between proof tags is formatted to 4k bytes
    public static @Nullable String buildXmpProofMetadata(@Nullable String sourceXmpString, String proofString) throws ProofException {
        byte[] newXmpBytes = null;
        byte[] sourceXmpBytes;

        StringBuffer newXmpString=new StringBuffer();
        String XMP_START = "<?xpacket begin='﻿' id='W5M0MpCehiHzreSzNTczkc9d'?>\n" +
                "<x:xmpmeta xmlns:x='adobe:ns:meta/'>\n" +
                "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n";
        String XMP_INTRO_PROOF =
                "<rdf:Description rdf:about='' xmlns:proof='http://technoprimates.com/proof/0.1/'>\n" +
                        "<proof:proof>";
        String XMP_CLOSE_PROOF = "</proof:proof>\n"+
                "</rdf:Description>\n";
        String XMP_END = "</rdf:RDF>\n </x:xmpmeta>\n<?xpacket end='w'?>";
        int offsetStartProofTag, offsetEndProofTag, offsetEndRDFTag;

        try {
            if (sourceXmpString == null || sourceXmpString.equals("")){
                // First case : create new xmp from scratch, with just proof metadata
                newXmpString.append(XMP_START);
                newXmpString.append(XMP_INTRO_PROOF);
                newXmpString.append(toProofTagFormat(proofString));
                newXmpString.append(XMP_CLOSE_PROOF);
                newXmpString.append(XMP_END);

            } else { 
                // create new xmp from old xmp
                // check proof tag presence in source
                offsetStartProofTag = sourceXmpString.indexOf("<proof:proof>"); // locate opening tag <proof:proof>
                offsetEndProofTag = sourceXmpString.indexOf("</proof:proof>"); // locate closing tag </proof:proof>
                if (offsetStartProofTag != -1) {
                    // Second case : proof tag already exists, overwrite with new proof metadata
                    // Control validity of source xmp
                    if ((offsetEndProofTag == -1)                 // invalid : no closing tag
                            || (offsetEndProofTag <= offsetStartProofTag)  // invalid : closing tag before opening tag
                            || (offsetEndProofTag + 14 >= sourceXmpString.length())) { // invalid : no stuff after closing tag
                        throw new ProofException(ProofError.ERROR_INVALID_XMP);
                    }//failed

                    // Build new xmp
                    // copy the existing stuff until end of opening tag "<proof:proof>"
                    newXmpString.append(sourceXmpString.substring(0, offsetStartProofTag+13));
                    // append proof text formatted to 4k string
                    newXmpString.append(toProofTagFormat(proofString));
                    // locate end of existing proof metadata
                    // copy the stuff after existing proof metadata and until end of xmp
                    newXmpString.append(sourceXmpString.substring(offsetEndProofTag));

                } else {
                    // Third case : metadata already exists, but without proof starting tag. Insert proof tags
                    offsetEndRDFTag = sourceXmpString.lastIndexOf("</rdf:RDF>"); // locate last closing tag </rdf:RDF>
                    newXmpString.append(sourceXmpString.substring(0, offsetEndRDFTag)); // copy until closing RDF tag not included
                    // append proof text formatted to 4k string
                    newXmpString.append(XMP_INTRO_PROOF + toProofTagFormat(proofString) + XMP_CLOSE_PROOF);
                    // append end of xmp structure
                    newXmpString.append(sourceXmpString.substring(offsetEndRDFTag));
                }
            }
        } catch (NullPointerException e) {
            throw new ProofException(ProofError.ERROR_INVALID_XMP);
        }
        return newXmpString.toString();
    }


    // extend proof String to String of fixed 4k byte length
    private static String toProofTagFormat(@Nullable String proofText) throws ProofException {
        String formattedString = null;
        if (proofText == null) proofText = "";
        try {
            byte[] bytes = new byte[4096];
            Arrays.fill(bytes, (byte) ' ');
            System.arraycopy(proofText.getBytes(StandardCharsets.UTF_8),
                    0,
                    bytes,
                    0,
                    proofText.getBytes(StandardCharsets.UTF_8).length);

            formattedString = new String(bytes, "UTF-8"); // string filled with spaces
        } catch (UnsupportedEncodingException e) {
            throw new ProofException(ProofError.ERROR_INVALID_XMP);
        }
        return formattedString;
    }


    // Extract proof text between xmp proof tags
    public static String getProofStringFromXmpMetadata(String xmpMetadata) throws ProofException {
        int proofStartOffset = xmpMetadata.indexOf("<proof:proof>"); // locate opening tag <proof:proof>
        int proofEndOffset = xmpMetadata.indexOf("</proof:proof>"); // locate closing tag </proof:proof>
        if ((proofStartOffset == -1) || (proofEndOffset == -1) || (proofEndOffset <= proofStartOffset)){
            throw new ProofException(ProofError.ERROR_NO_VALID_PROOF_FOUND);
        }
        return xmpMetadata.substring(proofStartOffset + 13, proofEndOffset);  // proof text filled up with spaces
    }
}


