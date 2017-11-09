/*
  Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package testsuite.x.devapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.concurrent.Callable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mysql.cj.api.xdevapi.DocResult;
import com.mysql.cj.core.ServerVersion;
import com.mysql.cj.x.core.XDevAPIError;
import com.mysql.cj.xdevapi.DbDoc;
import com.mysql.cj.xdevapi.JsonArray;
import com.mysql.cj.xdevapi.JsonNumber;
import com.mysql.cj.xdevapi.JsonParser;
import com.mysql.cj.xdevapi.JsonString;

/**
 * @todo
 */
public class CollectionModifyTest extends CollectionTest {
    @Before
    @Override
    public void setupCollectionTest() {
        super.setupCollectionTest();
    }

    @After
    @Override
    public void teardownCollectionTest() {
        super.teardownCollectionTest();
    }

    @Test
    public void testSet() {
        if (!this.isSetForXTests) {
            return;
        }
        this.collection.add("{}").execute();

        this.collection.modify("true").set("a", "Value for a").execute();
        this.collection.modify("1 == 1").set("b", "Value for b").execute();
        this.collection.modify("false").set("c", "Value for c").execute();
        this.collection.modify("0 == 1").set("d", "Value for d").execute();

        DocResult res = this.collection.find("a = 'Value for a'").execute();
        DbDoc jd = res.next();
        assertEquals("Value for a", ((JsonString) jd.get("a")).getString());
        assertEquals("Value for b", ((JsonString) jd.get("b")).getString());
        assertNull(jd.get("c"));
        assertNull(jd.get("d"));
    }

    @Test
    public void testUnset() {
        if (!this.isSetForXTests) {
            return;
        }
        this.collection.add("{\"x\":\"100\", \"y\":\"200\", \"z\":1}").execute();
        this.collection.add("{\"a\":\"100\", \"b\":\"200\", \"c\":1}").execute();

        this.collection.modify("true").unset("$.x").unset("$.y").execute();
        this.collection.modify("true").unset("$.a", "$.b").execute();

        DocResult res = this.collection.find().execute();
        DbDoc jd = res.next();
        assertNull(jd.get("x"));
        assertNull(jd.get("y"));
        assertNull(jd.get("a"));
        assertNull(jd.get("b"));
    }

    @Test
    public void testReplace() {
        if (!this.isSetForXTests) {
            return;
        }
        this.collection.add("{\"x\":100}").execute();
        this.collection.modify("true").change("$.x", "99").execute();

        DocResult res = this.collection.find().execute();
        DbDoc jd = res.next();
        assertEquals("99", ((JsonString) jd.get("x")).getString());
    }

    @Test
    public void testArrayAppend() {
        if (!this.isSetForXTests) {
            return;
        }
        this.collection.add("{\"x\":[8,16,32]}").execute();
        this.collection.modify("true").arrayAppend("$.x", "64").execute();

        DocResult res = this.collection.find().execute();
        DbDoc jd = res.next();
        JsonArray xArray = (JsonArray) jd.get("x");
        assertEquals(new Integer(8), ((JsonNumber) xArray.get(0)).getInteger());
        assertEquals(new Integer(16), ((JsonNumber) xArray.get(1)).getInteger());
        assertEquals(new Integer(32), ((JsonNumber) xArray.get(2)).getInteger());
        // TODO: better arrayAppend() overloads?
        assertEquals("64", ((JsonString) xArray.get(3)).getString());
        assertEquals(4, xArray.size());
    }

    @Test
    public void testArrayInsert() {
        if (!this.isSetForXTests) {
            return;
        }
        this.collection.add("{\"x\":[1,2]}").execute();
        this.collection.modify("true").arrayInsert("$.x[1]", 43).execute();
        // same as append
        this.collection.modify("true").arrayInsert("$.x[3]", 44).execute();

        DocResult res = this.collection.find().execute();
        DbDoc jd = res.next();
        JsonArray xArray = (JsonArray) jd.get("x");
        assertEquals(new Integer(1), ((JsonNumber) xArray.get(0)).getInteger());
        assertEquals(new Integer(43), ((JsonNumber) xArray.get(1)).getInteger());
        assertEquals(new Integer(2), ((JsonNumber) xArray.get(2)).getInteger());
        assertEquals(new Integer(44), ((JsonNumber) xArray.get(3)).getInteger());
        assertEquals(4, xArray.size());
    }

    @Test
    public void testJsonModify() {
        if (!this.isSetForXTests) {
            return;
        }

        DbDoc nestedDoc = new DbDoc().add("z", new JsonNumber().setValue("100"));
        DbDoc doc = new DbDoc().add("x", new JsonNumber().setValue("3")).add("y", nestedDoc);

        this.collection.add("{\"x\":1, \"y\":1}").execute();
        this.collection.add("{\"x\":2, \"y\":2}").execute();
        this.collection.add(doc).execute();
        this.collection.add("{\"x\":4, \"m\":1}").execute();

        assertThrows(XDevAPIError.class, "Parameter 'criteria' must not be null or empty.", new Callable<Void>() {
            public Void call() throws Exception {
                CollectionModifyTest.this.collection.modify(null).set("y", nestedDoc).execute();
                return null;
            }
        });

        assertThrows(XDevAPIError.class, "Parameter 'criteria' must not be null or empty.", new Callable<Void>() {
            public Void call() throws Exception {
                CollectionModifyTest.this.collection.modify(" ").set("y", nestedDoc).execute();
                return null;
            }
        });

        this.collection.modify("y = 1").set("y", nestedDoc).execute();
        this.collection.modify("y = :n").set("y", nestedDoc).bind("n", 2).execute();

        this.collection.modify("x = 1").set("m", 1).execute();
        this.collection.modify("true").change("$.m", nestedDoc).execute();

        assertEquals(1, this.collection.find("x = :x").bind("x", 1).execute().count());
        assertEquals(0, this.collection.find("y = :y").bind("y", 2).execute().count());
        assertEquals(3, this.collection.find("y = {\"z\": 100}").execute().count());
        assertEquals(2, this.collection.find("m = {\"z\": 100}").execute().count());

        // TODO check later whether it's possible; for now placeholders are of Scalar type only
        //assertEquals(1, this.collection.find("y = :y").bind("y", nestedDoc).execute().count());

        // literal won't match JSON docs
        assertEquals(0, this.collection.find("y = :y").bind("y", "{\"z\": 100}").execute().count());

        DocResult res = this.collection.find().execute();
        while (res.hasNext()) {
            DbDoc jd = res.next();
            if (jd.get("y") != null) {
                assertEquals(nestedDoc.toString(), ((DbDoc) jd.get("y")).toString());
            }
            if (jd.get("m") != null) {
                assertEquals(nestedDoc.toString(), ((DbDoc) jd.get("m")).toString());
            }
        }
    }

    @Test
    public void testArrayModify() {
        if (!this.isSetForXTests) {
            return;
        }

        JsonArray xArray = new JsonArray().addValue(new JsonString().setValue("a")).addValue(new JsonNumber().setValue("1"));
        DbDoc doc = new DbDoc().add("x", new JsonNumber().setValue("3")).add("y", xArray);

        this.collection.add("{\"x\":1, \"y\":[\"b\", 2]}").execute();
        this.collection.add("{\"x\":2, \"y\":22}").execute();
        this.collection.add(doc).execute();

        this.collection.modify("true").arrayInsert("$.y[1]", 44).execute();
        this.collection.modify("x = 2").change("$.y", xArray).execute();
        this.collection.modify("x = 3").set("y", xArray).execute();

        DocResult res = this.collection.find().execute();
        while (res.hasNext()) {
            DbDoc jd = res.next();
            if (((JsonNumber) jd.get("x")).getInteger() == 1) {
                assertEquals((new JsonArray().addValue(new JsonString().setValue("b")).addValue(new JsonNumber().setValue("44"))
                        .addValue(new JsonNumber().setValue("2"))).toString(), (jd.get("y")).toString());
            } else {
                assertEquals(xArray.toString(), jd.get("y").toString());
            }
        }

    }

    /**
     * Tests fix for BUG#24471057, UPDATE FAILS WHEN THE NEW VALUE IS OF TYPE DBDOC WHICH HAS ARRAY IN IT.
     * 
     * @throws Exception
     *             if the test fails.
     */
    @Test
    public void testBug24471057() throws Exception {
        if (!this.isSetForXTests) {
            return;
        }

        String docStr = "{\"B\" : 2, \"ID\" : 1, \"KEY\" : [1]}";
        DbDoc doc1 = JsonParser.parseDoc(new StringReader(docStr));

        this.collection.add(doc1).execute();
        this.collection.modify("ID=1").set("$.B", doc1).execute();

        // expected doc
        DbDoc doc2 = JsonParser.parseDoc(new StringReader(docStr));
        doc2.put("B", doc1);
        doc2.put("_id", doc1.get("_id"));
        DocResult docs = this.collection.find().execute();
        DbDoc doc = docs.next();
        assertEquals(doc2.toString(), doc.toString());

        // DbDoc as an array member
        DbDoc doc3 = JsonParser.parseDoc(new StringReader(docStr));
        ((JsonArray) doc1.get("KEY")).add(doc3);
        this.collection.modify("ID=1").set("$.B", doc1).execute();

        // expected doc
        doc2.put("B", doc1);
        docs = this.collection.find().execute();
        doc = docs.next();
        assertEquals(doc2.toString(), doc.toString());

    }

    @Test
    public void testMergePatch() throws Exception {
        if (!this.isSetForXTests || !mysqlVersionMeetsMinimum(ServerVersion.parseVersion("8.0.3"))) {
            return;
        }

        // 1. Update the name and zip code of match
        this.collection.add("{\"_id\": \"1\", \"name\": \"Alice\", \"address\": {\"zip\": \"12345\", \"street\": \"32 Main str\"}}").execute();
        this.collection.add("{\"_id\": \"2\", \"name\": \"Bob\", \"address\": {\"zip\": \"325226\", \"city\": \"San Francisco\", \"street\": \"42 2nd str\"}}")
                .execute();

        this.collection.modify("_id = :id").patch(JsonParser.parseDoc(new StringReader("{\"name\": \"Joe\", \"address\": {\"zip\":\"91234\"}}")))
                .bind("id", "1").execute();

        DocResult docs = this.collection.find().orderBy("$._id").execute();
        assertTrue(docs.hasNext());
        assertEquals(JsonParser
                .parseDoc(new StringReader("{\"_id\": \"1\", \"name\": \"Joe\", \"address\": {\"zip\": \"91234\", \"street\": \"32 Main str\"}}")).toString(),
                docs.next().toString());
        assertTrue(docs.hasNext());
        assertEquals(JsonParser
                .parseDoc(new StringReader(
                        "{\"_id\": \"2\", \"name\": \"Bob\", \"address\": {\"zip\": \"325226\", \"city\": \"San Francisco\", \"street\": \"42 2nd str\"}}"))
                .toString(), docs.next().toString());
        assertFalse(docs.hasNext());

        // 2. Delete the address field of match
        this.collection.modify("_id = :id").patch("{\"address\": null}").bind("id", "1").execute();

        docs = this.collection.find().orderBy("$._id").execute();
        assertTrue(docs.hasNext());
        assertEquals(JsonParser.parseDoc(new StringReader("{\"_id\": \"1\", \"name\": \"Joe\"}")).toString(), docs.next().toString());
        assertTrue(docs.hasNext());
        assertEquals(JsonParser
                .parseDoc(new StringReader(
                        "{\"_id\": \"2\", \"name\": \"Bob\", \"address\": {\"zip\": \"325226\", \"city\": \"San Francisco\", \"street\": \"42 2nd str\"}}"))
                .toString(), docs.next().toString());
        assertFalse(docs.hasNext());

        String id = "a6f4b93e1a264a108393524f29546a8c";
        this.collection.add("{\"_id\" : \"" + id + "\"," //
                + "\"title\" : \"AFRICAN EGG\"," //
                + "\"description\" : \"A Fast-Paced Documentary of a Pastry Chef And a Dentist who must Pursue a Forensic Psychologist in The Gulf of Mexico\"," //
                + "\"releaseyear\" : 2006," //
                + "\"language\" : \"English\"," //
                + "\"duration\" : 130," //
                + "\"rating\" : \"G\"," //
                + "\"genre\" : \"Science fiction\"," //
                + "\"actors\" : [" //
                + "    {\"name\" : \"MILLA PECK\"," //
                + "     \"country\" : \"Mexico\"," //
                + "     \"birthdate\": \"12 Jan 1984\"}," //
                + "    {\"name\" : \"VAL BOLGER\"," //
                + "     \"country\" : \"Botswana\"," //
                + "     \"birthdate\": \"26 Jul 1975\" }," //
                + "    {\"name\" : \"SCARLETT BENING\"," //
                + "     \"country\" : \"Syria\"," //
                + "     \"birthdate\": \"16 Mar 1978\" }" //
                + "    ]," //
                + "\"additionalinfo\" : {" //
                + "    \"director\" : {" //
                + "        \"name\": \"Sharice Legaspi\"," //
                + "        \"age\":57," //
                + "        \"awards\": [" //
                + "            {\"award\": \"Best Movie\"," //
                + "             \"movie\": \"THE EGG\"," //
                + "             \"year\": 2002}," //
                + "            {\"award\": \"Best Special Effects\"," //
                + "             \"movie\": \"AFRICAN EGG\"," //
                + "             \"year\": 2006}" //
                + "            ]" //
                + "        }," //
                + "    \"writers\" : [\"Rusty Couturier\", \"Angelic Orduno\", \"Carin Postell\"]," //
                + "    \"productioncompanies\" : [\"Qvodrill\", \"Indigoholdings\"]" //
                + "    }" //
                + "}").execute();

        // Adding a new field to multiple documents
        this.collection.modify("language = :lang").patch("{\"translations\": [\"Spanish\"]}").bind("lang", "English").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        DbDoc doc = docs.next();
        assertNotNull(doc.get("translations"));
        JsonArray arr = (JsonArray) doc.get("translations");
        assertEquals(1, arr.size());
        assertEquals("Spanish", ((JsonString) arr.get(0)).getString());

        this.collection.modify("additionalinfo.director.name = :director").patch("{\"additionalinfo\": {\"musicby\": \"Sakila D\" }}")
                .bind("director", "Sharice Legaspi").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("additionalinfo"));
        DbDoc doc2 = (DbDoc) doc.get("additionalinfo");
        assertNotNull(doc2.get("musicby"));
        assertEquals("Sakila D", ((JsonString) doc2.get("musicby")).getString());

        this.collection.modify("additionalinfo.director.name = :director").patch("{\"additionalinfo\": {\"director\": {\"country\": \"France\"}}}")
                .bind("director", "Sharice Legaspi").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("additionalinfo"));
        assertNotNull(((DbDoc) doc.get("additionalinfo")).get("director"));
        doc2 = (DbDoc) ((DbDoc) doc.get("additionalinfo")).get("director");
        assertNotNull(doc2.get("country"));
        assertEquals("France", ((JsonString) doc2.get("country")).getString());

        // Replacing/Updating a field's value in multiple documents
        this.collection.modify("language = :lang").patch("{\"translations\": [\"Spanish\", \"Italian\"]}").bind("lang", "English").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("translations"));
        arr = (JsonArray) doc.get("translations");
        assertEquals(2, arr.size());
        assertEquals("Spanish", ((JsonString) arr.get(0)).getString());
        assertEquals("Italian", ((JsonString) arr.get(1)).getString());

        this.collection.modify("additionalinfo.director.name = :director").patch("{\"additionalinfo\": {\"musicby\": \"The Sakila\" }}")
                .bind("director", "Sharice Legaspi").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("additionalinfo"));
        doc2 = (DbDoc) doc.get("additionalinfo");
        assertNotNull(doc2.get("musicby"));
        assertEquals("The Sakila", ((JsonString) doc2.get("musicby")).getString());

        this.collection.modify("additionalinfo.director.name = :director").patch("{\"additionalinfo\": {\"director\": {\"country\": \"Canada\"}}}")
                .bind("director", "Sharice Legaspi").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("additionalinfo"));
        assertNotNull(((DbDoc) doc.get("additionalinfo")).get("director"));
        doc2 = (DbDoc) ((DbDoc) doc.get("additionalinfo")).get("director");
        assertNotNull(doc2.get("country"));
        assertEquals("Canada", ((JsonString) doc2.get("country")).getString());

        // Removing a field from multiple documents:
        this.collection.modify("language = :lang").patch("{\"translations\": null}").bind("lang", "English").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNull(doc.get("translations"));

        //            coll.modify('additionalinfo.director.name = :director').patch({ "additionalinfo": { "musicby": null }}).bind('director','Sharice Legaspi').execute();
        this.collection.modify("additionalinfo.director.name = :director").patch("{\"additionalinfo\": {\"musicby\": null }}")
                .bind("director", "Sharice Legaspi").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("additionalinfo"));
        doc2 = (DbDoc) doc.get("additionalinfo");
        assertNull(doc2.get("musicby"));

        this.collection.modify("additionalinfo.director.name = :director").patch("{\"additionalinfo\": {\"director\": {\"country\": null}}}")
                .bind("director", "Sharice Legaspi").execute();
        docs = this.collection.find("_id = :id").bind("id", id).limit(1).execute();
        assertTrue(docs.hasNext());
        doc = docs.next();
        assertNotNull(doc.get("additionalinfo"));
        assertNotNull(((DbDoc) doc.get("additionalinfo")).get("director"));
        doc2 = (DbDoc) ((DbDoc) doc.get("additionalinfo")).get("director");
        assertNull(doc2.get("country"));
    }
}