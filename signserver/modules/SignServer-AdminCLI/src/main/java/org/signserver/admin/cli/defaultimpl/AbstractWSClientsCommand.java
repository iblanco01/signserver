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
package org.signserver.admin.cli.defaultimpl;

import java.security.cert.X509Certificate;
import java.util.List;

import javax.ejb.EJBException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.ejbca.util.CertTools;
import org.signserver.admin.cli.defaultimpl.AbstractAdminCommand.ClientEntry;
import org.signserver.cli.spi.CommandFailureException;
import org.signserver.cli.spi.IllegalCommandArgumentsException;
import org.signserver.cli.spi.UnexpectedCommandFailureException;
import org.signserver.common.GlobalConfiguration;
import org.signserver.common.SignServerUtil;

/**
 * Abstract implementation for commands adding list of WS users
 * (identified by client certificates).
 * 
 * @author Marcus Lundblad
 * @version $Id$
 */
public abstract class AbstractWSClientsCommand extends AbstractAdminCommand {

    public static final String ADD = "add";
    public static final String REMOVE = "remove";
    public static final String LIST = "list";
    public static final String CERTSERIALNO = "certserialno";
    public static final String ISSUERDN = "issuerdn";
    public static final String CERT = "cert";

    /** The command line options. */
    protected static final Options OPTIONS;
    
    static {
        OPTIONS = new Options();
        OPTIONS.addOption(ADD, false, "Add a new WS auditor");
        OPTIONS.addOption(REMOVE, false, "Remove a WS auditor");
        OPTIONS.addOption(LIST, false, "List all WS auditors");

        OPTIONS.addOption(CERTSERIALNO, true,
                "Subject certificate serial number");
        OPTIONS.addOption(ISSUERDN, true,
                "Issuer distinguished name");
        OPTIONS.addOption(CERT, true, "Certificate file");
    }
    
    private String operation;
    private String certSerialNo;
    private String issuerDN;
    private String cert;

    /**
     * Reads all the options from the command line.
     *
     * @param line The command line to read from
     */
    private void parseCommandLine(final CommandLine line) {
        certSerialNo = line.getOptionValue(CERTSERIALNO, null);
        issuerDN = line.getOptionValue(ISSUERDN, null);
        cert = line.getOptionValue(CERT, null);
        if (line.hasOption(ADD)) {
            operation = ADD;
        } else if (line.hasOption(REMOVE)) {
            operation = REMOVE;
        } else if (line.hasOption(LIST)) {
            operation = LIST;
        }
    }

    /**
     * Checks that all mandatory options are given.
     */
    private void validateOptions() throws IllegalCommandArgumentsException {
        if (operation == null) {
            throw new IllegalCommandArgumentsException("Missing operation: -add, -remove or -list");
        } else if (!operation.equals(LIST)) {
                if (cert != null) {
                        // don't allow -cert option in combination with -certserialno and -issuerdn
                        if (certSerialNo != null || issuerDN != null) {
                            throw new IllegalCommandArgumentsException("Can't use the option -cert at the same time as -certserialno and -issuerdn");
                        }
                } else {
                        if (certSerialNo == null) {
                                throw new IllegalCommandArgumentsException("Missing option: -certserialno");
                        } else if (issuerDN == null) {
                                throw new IllegalCommandArgumentsException("Missing option: -issuerdn");
                        }
                }
        }
    }

    @Override
    public int execute(String... args) throws IllegalCommandArgumentsException, CommandFailureException, UnexpectedCommandFailureException {
        try {
            // Parse the command line
            parseCommandLine(new GnuParser().parse(OPTIONS, args));
        } catch (ParseException ex) {
            throw new IllegalCommandArgumentsException(ex.getMessage());
        }
        validateOptions();
        
        try {
            final String admins = getGlobalConfigurationSession().getGlobalConfiguration().getProperty(
                    GlobalConfiguration.SCOPE_GLOBAL, getClientsProperty());
            final List<ClientEntry> entries = parseClientEntries(admins);

            if (LIST.equals(operation)) {
                final StringBuilder buff = new StringBuilder();
                buff.append("Authorized auditors:");
                buff.append("\n");
                for (ClientEntry entry : entries) {
                    buff.append(String.format("%-20s %s",
                            entry.getCertSerialNo(), entry.getIssuerDN()));
                    buff.append("\n");
                }
                getOutputStream().println(buff.toString());
            } else if (ADD.equals(operation)) {
                if (cert == null) {
                        // serial number and issuer DN was entered manually
                        entries.add(new ClientEntry(certSerialNo, issuerDN));
                } else {
                        // read serial number and issuer DN from cert file
                        X509Certificate certificate = SignServerUtil.getCertFromFile(cert);
                        String sn = certificate.getSerialNumber().toString(16);
                        String dn = certificate.getIssuerX500Principal().getName();
                        
                        CertTools.BasicX509NameTokenizer tok = new CertTools.BasicX509NameTokenizer(dn);
                        StringBuilder buf = new StringBuilder();

                        while (tok.hasMoreTokens()) {
                                final String token = tok.nextToken();
                                buf.append(token);
                                if (tok.hasMoreTokens()) {
                                        buf.append(", ");
                                }
                        }
                        
                        entries.add(new ClientEntry(sn, buf.toString()));
                }
                getGlobalConfigurationSession().setProperty(
                        GlobalConfiguration.SCOPE_GLOBAL, getClientsProperty(),
                        serializeClientEntries(entries));
                getOutputStream().println("Auditor added");
            } else if (REMOVE.equals(operation)) {
                if (entries.remove(new ClientEntry(certSerialNo, issuerDN))) {
                    getGlobalConfigurationSession().setProperty(
                            GlobalConfiguration.SCOPE_GLOBAL, getClientsProperty(),
                            serializeClientEntries(entries));
                    getOutputStream().println("Auditor removed");
                } else {
                    getErrorStream().println("No such auditor");
                }
            }
            return 0;
        } catch (EJBException eJBException) {
            if (eJBException.getCausedByException() instanceof IllegalArgumentException) {
                getErrorStream().println(eJBException.getMessage());
                return -2;
            } else {
                throw new UnexpectedCommandFailureException(eJBException);
            }
        } catch (Exception e) {
            throw new UnexpectedCommandFailureException(e);
        }
    }
 
    /**
     * Return the global property used to store the client list.
     * 
     * @return
     */
    protected abstract String getClientsProperty();
    
}