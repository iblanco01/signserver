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
package org.signserver.common;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TODO: Document me!
 *
 * @version @Id$
 */
public class SignServerUtilTest {

    private static String signserverhome;

    @Before
    public void setUp() throws Exception {
        signserverhome = System.getenv("SIGNSERVER_HOME");
        assertNotNull(signserverhome);
    }

    @Test
    public void testGetCollectionOfValuesFromProperties() {
        WorkerConfig wp = new WorkerConfig();
        wp.setProperty("test.test1.0", "VALID0");
        wp.setProperty("test.test2.", "NOVALID");
        wp.setProperty("test.test2.10", "VALID10");
        wp.setProperty("test.test2.2", "VALID2");
        wp.setProperty("test.test2.7", "VALID7");

        List<String> values = SignServerUtil.getCollectionOfValuesFromProperties("test.test2.", wp);
        assertTrue(values.size() == 11);
        assertTrue(values.get(0) == "");
        assertTrue(values.get(1) == "");
        assertTrue(values.get(2) == "VALID2");
        assertTrue(values.get(3) == "");
        assertTrue(values.get(6) == "");
        assertTrue(values.get(7) == "VALID7");
        assertTrue(values.get(8) == "");
        assertTrue(values.get(9) == "");
        assertTrue(values.get(10) == "VALID10");

        values = SignServerUtil.getCollectionOfValuesFromProperties("test.test1.", wp);
        assertTrue(values.size() == 1);
        assertTrue(values.get(0) == "VALID0");

        values = SignServerUtil.getCollectionOfValuesFromProperties("test.test3.", wp);
        assertTrue(values.size() == 0);
    }

    @Test
    public void testReadValueFromConfigFile() throws IOException {
        String confPath = signserverhome + "/tmp/testsignserverutil.test";
        File confFile = new File(confPath);
        FileWriter fw = new FileWriter(confFile);
        fw.write("#test \n");
        fw.write("SIGNSERVER_SASD = asdf\n");
        fw.write("SIGNSERVER_NODEID   =  NODE1\n");
        fw.write("SIGNSERVER_NODEID2=NODE2\n");
        fw.write("SIGNSERVER_NODEID3 =NODE3\n");
        fw.write("SIGNSERVER_NODEID4= NODE4\n");
        fw.write("#SIGNSERVER_NODEID5= NODE5\n");
        fw.close();

        assertTrue(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID", confFile).equals("NODE1"));
        assertTrue(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID2", confFile).equals("NODE2"));
        assertTrue(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID3", confFile).equals("NODE3"));
        assertTrue(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID4", confFile).equals("NODE4"));
        assertNull(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID5", confFile));
        assertNull(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID6", confFile));

        fw = new FileWriter(confFile);
        fw.write("SIGNSERVER_NODEID   =  NODE1\n");
        fw.close();
        assertTrue(SignServerUtil.readValueFromConfigFile("SIGNSERVER_NODEID", confFile).equals("NODE1"));
        assertTrue(SignServerUtil.readValueFromConfigFile("signserver_NODEID", confFile).equals("NODE1"));
    }
}