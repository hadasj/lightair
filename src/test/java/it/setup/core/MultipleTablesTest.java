package it.setup.core;

import static org.junit.Assert.*;
import it.common.CommonTestBase;

import java.util.List;
import java.util.Map;

import net.sf.lightair.LightAir;
import net.sf.lightair.annotation.Setup;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LightAir.class)
@Setup
public class MultipleTablesTest extends CommonTestBase {

	List<Map<String, Object>> values;

	@BeforeClass
	public static void beforeClass() {
		db.execute("create table a (id int primary key, a1 varchar(50))");
		db.execute("create table b (id int primary key, b1 varchar(50))");
		db.execute("create table c (id int primary key, c1 varchar(50))");
	}

	@AfterClass
	public static void afterClass() {
		db.execute("drop table a");
		db.execute("drop table b");
		db.execute("drop table c");
	}

	@Test
	public void tableRowsTogether() {
		verify();
	}

	@Test
	public void tableRowsMixed() {
		verify();
	}

	private void verify() {
		verifyTable("a");
		verifyTable("b");
		verifyTable("c");
	}

	private void verifyTable(String tableName) {
		int count = 3;
		assertEquals("Count", count,
				db.queryForInt("select count(*) from " + tableName));
		values = db.queryForList("select * from " + tableName);
		for (int i = 0; i < count; i++) {
			verifyRow(i, tableName + "1", tableName + "1" + i);
		}
	}

	private void verifyRow(int id, String column, String value) {
		assertEquals("id " + id, id, values.get(id).get("id"));
		assertEquals(column + " " + id, value, values.get(id).get(column));
	}

}