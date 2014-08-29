/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.admin.cli.defaultimpl.archive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.signserver.admin.cli.AdminCLIUtils;
import org.signserver.admin.cli.defaultimpl.AdminCommandHelper;
import org.signserver.cli.spi.AbstractCommand;
import org.signserver.cli.spi.CommandFailureException;
import org.signserver.cli.spi.IllegalCommandArgumentsException;
import org.signserver.cli.spi.UnexpectedCommandFailureException;
import org.signserver.common.ArchiveDataVO;
import org.signserver.common.ArchiveMetadata;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.cesecore.util.query.Criteria;
import org.cesecore.util.query.Elem;
import org.cesecore.util.query.QueryCriteria;
import org.cesecore.util.query.elems.RelationalOperator;
import org.cesecore.util.query.elems.Term;

/**
 * Query contents of the archive.
 *
 * @author Marcus Lundblad
 * @version $Id$
 */
public class QueryArchiveCommand extends AbstractCommand {

    private AdminCommandHelper helper = new AdminCommandHelper();
    
    /** Option strings */
    public static final String FROM = "from";
    public static final String LIMIT = "limit";
    public static final String CRITERIA = "criteria";
    public static final String HEADER = "header";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String OUTPATH = "outpath";
    
    /** The command line options */
    private static final Options OPTIONS;
    private static final Set<String> intFields;
    private static final Set<String> dateFields;
    private static final Set<RelationalOperator> noArgOps;
    private static final Set<String> allowedFields;
    
    private int from;
    private int limit;
    private boolean printHeader;
    private QueryCriteria qc;
    private File outPath;
    
    private static final String HEADER_FIELDS = "archiveid, time, type, signerid, requestIssuerDN, requestCertSerialNumber, requestIP";
    private static final String HEADER_NAMES =  "Archive ID, Time, Type, Signer ID, Issuer DN, Certificate Serial Number, IP Address";

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
    
    static {
        OPTIONS = new Options();
        OPTIONS.addOption(CRITERIA, true, "Search criteria (can specify multiple criterias)");
        OPTIONS.addOption(FROM, true, "Lower index in search result (0-based)");
        OPTIONS.addOption(LIMIT, true, "Maximum number of search results");
        OPTIONS.addOption(HEADER, false, "Print a column header");
        OPTIONS.addOption(REQUEST, false, "Search for requests");
        OPTIONS.addOption(RESPONSE, false, "Search for responses");
        OPTIONS.addOption(OUTPATH, true, "Directory to write output to");
    
        intFields = new HashSet<String>();
        intFields.add(ArchiveMetadata.SIGNER_ID);
        
        dateFields = new HashSet<String>();
        dateFields.add(ArchiveMetadata.TIME);
        
        noArgOps = new HashSet<RelationalOperator>();
        noArgOps.add(RelationalOperator.NULL);
        noArgOps.add(RelationalOperator.NOTNULL);
     
        allowedFields = new HashSet<String>();
        allowedFields.add(ArchiveMetadata.ARCHIVE_ID);
        allowedFields.add(ArchiveMetadata.REQUEST_CERT_SERIAL_NUMBER);
        allowedFields.add(ArchiveMetadata.REQUEST_IP);
        allowedFields.add(ArchiveMetadata.REQUEST_ISSUER_DN);
        allowedFields.add(ArchiveMetadata.SIGNER_ID);
        allowedFields.add(ArchiveMetadata.TIME);
        allowedFields.add(ArchiveMetadata.TYPE);
        allowedFields.add(ArchiveMetadata.UNIQUE_ID);
    }
        
    @Override
    public String getDescription() {
        return "Query the content of the archive";
    }

    @Override
    public String getUsages() {
        return "Usage: signserver archive query -limit <number> [-criteria  \"<field> <op> <value>\" [-criteria...]] [-from <index>] [-header] [-request|-response] [-outpath <path>]\n"
        + "<field> is a field name from the archive: archiveid, requestCertSerialnumber, requestIP, requestIssuerDN, signerid, time, type, uniqueId\n"
        + "<op> is a relational operator: GT, GE, LT, LE, EQ, NEQ, LIKE, NULL, NOTNULL\n"
	+ "-request shows only entries for requests\n"
	+ "-response shows only entries for responses\n"
        + "if the -outpath option is given, archive data for the matched items are written to the given directory\n"
        + "Example: signserver archive query -limit 10 -criteria \"signerid EQ 1\"\n"
	+ "Example: signserver archive query -limit 10 -criteria \"signerid EQ 1\" -request\n"
        + "Example: signserver archive query -limit 10 -criteria \"time GT 1359623137000\" -criteria \"requestIP EQ 127.0.0.1\"\n"
        + "Example: signserver archive query -limit 10 -criteria \"signerid EQ 1\" -outpath /tmp/out\n\n";
    }

    @Override
    public int execute(String... args) throws IllegalCommandArgumentsException,
            CommandFailureException, UnexpectedCommandFailureException {
        qc = QueryCriteria.create().add(Criteria.orderDesc(ArchiveMetadata.TIME));
        
        try {
            parseCommandLine(new GnuParser().parse(OPTIONS, args));
        } catch (ParseException ex) {
            throw new IllegalCommandArgumentsException(ex.getMessage());
        }
        
        try {
            // if an output path was specified, download data for result entries
            final boolean downloadData = (outPath != null);
            int downloadedItems = 0;
            
            if (printHeader) {
                out.println(HEADER_NAMES);
                out.println(HEADER_FIELDS);
            }
 
            // Perform the query
            List<? extends ArchiveMetadata> entries =
                    helper.getWorkerSession().searchArchive(from, limit, qc, downloadData);
    
            for (final ArchiveMetadata entry : entries) {
                // render the result
                final StringBuilder buff = new StringBuilder();
                final String type = ArchiveMetadata.getTypeName(entry.getType());
                final String issuer =
                        entry.getRequestIssuerDN() != null ? entry.getRequestIssuerDN() : "";
                final String serial =
                        entry.getRequestCertSerialNumber() != null ?
                                entry.getRequestCertSerialNumber() : "";
                final String ip =
                        entry.getRequestIP() != null ? entry.getRequestIP() : "Local EJB";
                
                buff.append(entry.getArchiveId()).append(", ")
                    .append(sdf.format(entry.getTime())).append(", ")
                    .append(type).append(", ")
                    .append(entry.getSignerId()).append(", ")
                    .append(issuer).append(", ")
                    .append(serial).append(", ")
                    .append(ip);
            
                out.println(buff.toString());
                
                if (downloadData) {
                    saveEntry(entry);
                    downloadedItems++;
                }
            }
            
            if (downloadData) {
                out.print(String.format("\nDownloaded %d archive entries",
                        downloadedItems));
            }
            
            out.println("\n\n");
            return 0;
        } catch (Exception e) {
            throw new UnexpectedCommandFailureException(e);
        }
    }
    
    private void saveEntry(final ArchiveMetadata entry) throws IOException {
        final String fileName = entry.suggestedFilename();
        final File outfile = new File(outPath, fileName);
        final FileOutputStream fis = new FileOutputStream(outfile);
        
        fis.write(entry.getArchiveData());
    }
    
    private void parseCommandLine(final CommandLine line) throws ParseException {
        final String fromString = line.getOptionValue(FROM);
        final String limitString = line.getOptionValue(LIMIT);
        
        printHeader = line.hasOption(HEADER);
        
        if (fromString != null) {
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException ex) {
                throw new ParseException("Invalid from index value: " + fromString);
            }
        }
        
        if (limitString != null) {
            try {
                limit = Integer.parseInt(limitString);
                
                if (limit <= 0) {
                    throw new ParseException("Too small value specified for limit: " + limit);
                }
            } catch (NumberFormatException ex) {
                throw new ParseException("Invalid limit value: " + limitString);
            }
        } else {
            throw new ParseException("Must specify a limit.");
        }
        
        if (line.hasOption(REQUEST) && line.hasOption(RESPONSE)) {
            throw new ParseException("Can not specify both -request and -response at the same time");
        } else if (line.hasOption(REQUEST)) {
            qc.add(new Term(RelationalOperator.EQ, ArchiveMetadata.TYPE, ArchiveDataVO.TYPE_REQUEST));
        } else if (line.hasOption(RESPONSE)) {
            qc.add(new Term(RelationalOperator.EQ, ArchiveMetadata.TYPE, ArchiveDataVO.TYPE_RESPONSE));
        }
        
        if (line.hasOption(OUTPATH)) {
            outPath = new File(line.getOptionValue(OUTPATH));
            
            if (!outPath.isDirectory()) {
                throw new ParseException("Output path must be a directory");
            }
        }
        
        final String[] criterias = line.getOptionValues(CRITERIA);
        
        final List<Elem> terms = new LinkedList<Elem>();
        
        if (criterias != null && criterias.length > 0) {
            for (final String criteria : criterias) {
                try {
                    final Term term = parseCriteria(criteria);
                    terms.add(term);
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid critera, expected a numeric value: " + criteria);
                } catch (IllegalArgumentException e) {
                    throw new ParseException("Invalid critera specified: " + e.getMessage() + ": " + 
                            criteria);
                } catch (java.text.ParseException e) {
                    throw new ParseException("Invalid date specified: " + criteria);
                }
            }
        
            Elem all = AdminCLIUtils.andAll(terms, 0);
            qc.add(all);
        } 
    }
    
    static Term parseCriteria(final String criteria)
        throws IllegalArgumentException, NumberFormatException, java.text.ParseException {
        return AdminCLIUtils.parseCriteria(criteria, allowedFields, 
                noArgOps, intFields, Collections.<String>emptySet(), dateFields);
    }
}
