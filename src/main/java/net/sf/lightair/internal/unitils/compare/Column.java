package net.sf.lightair.internal.unitils.compare;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;

import net.sf.lightair.internal.util.AutoValueGenerator;

import org.apache.commons.codec.binary.Base64;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.unitils.core.UnitilsException;
import org.unitils.dbunit.dataset.comparison.ColumnDifference;

/**
 * Fork of Unitils Column to allow for customization.
 */
public class Column extends org.unitils.dbunit.dataset.Column {

	private final String tableName;
	private final int columnLength;
	private Object value;

	/**
	 * Constructor.
	 * 
	 * @param tableName
	 *            Table name
	 * @param name
	 *            Column name
	 * @param type
	 *            Column type
	 * @param columnLength
	 *            Column length
	 * @param value
	 *            Column value
	 */
	public Column(String tableName, String name, DataType type,
			int columnLength, Object value) {
		super(name, type, value);
		this.tableName = tableName;
		this.columnLength = columnLength;
		this.value = value;
	}

	@Override
	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	// Extracted to support variables

	/**
	 * Pre-compare this expected column value with actual column value.
	 * <p>
	 * If expected value is a variable, it is considered a non-match.
	 * 
	 * @param actualColumn
	 *            Actual column
	 * @return <code>null</code> if values match, {@link ColumnDifference}
	 *         otherwise
	 */
	public ColumnDifference preCompare(
			org.unitils.dbunit.dataset.Column actualColumn) {
		if (isAny()) {
			return getDifferenceForAny(actualColumn);
		}
		if (isAuto()) {
			Object value = autoValueGenerator.generateAutoValue(
					actualColumn.getType(), tableName, actualColumn.getName(),
					columnLength);
			setValue(value);
		}
		if (valuesSame(actualColumn)) {
			return null;
		}
		if (valueNullAndActualNotNull(actualColumn)) {
			return new ColumnDifference(this, actualColumn);
		}
		if (variableResolver.isVariable(getValue())) {
			return new ColumnDifference(this, actualColumn);
		}
		if (!isCastedValueEqual(getValue(), actualColumn)) {
			return createDifferenceForCasted(getValue(), actualColumn);
		}
		return null;
	}

	/**
	 * Compare this expected column value with actual column value.
	 * <p>
	 * If expected value is a variable, its value is resolved.
	 * 
	 * @param actualColumn
	 *            Actual column
	 * @return <code>null</code> if values match, {@link ColumnDifference}
	 *         otherwise
	 */
	@Override
	public ColumnDifference compare(
			org.unitils.dbunit.dataset.Column actualColumn) {
		if (isAny()) {
			return getDifferenceForAny(actualColumn);
		}
		if (valuesSame(actualColumn)) {
			return null;
		}
		if (valueNullAndActualNotNull(actualColumn)) {
			return new ColumnDifference(this, actualColumn);
		}
		Object value = variableResolver.resolveValue(getValue(),
				actualColumn.getValue());
		if (!isCastedValueEqual(value, actualColumn)) {
			return createDifferenceForCasted(value, actualColumn);
		}
		return null;
	}

	private boolean isAny() {
		return "@any".equals(getValue());
	}

	private boolean isAuto() {
		return "@auto".equals(getValue());
	}

	private ColumnDifference getDifferenceForAny(
			org.unitils.dbunit.dataset.Column actualColumn) {
		if (null == actualColumn.getValue()) {
			return new ColumnDifference(this, actualColumn);
		}
		return null;
	}

	private boolean valuesSame(org.unitils.dbunit.dataset.Column actualColumn) {
		return (getValue() == actualColumn.getValue());
	}

	private boolean valueNullAndActualNotNull(
			org.unitils.dbunit.dataset.Column actualColumn) {
		return (getValue() == null && actualColumn.getValue() != null);
	}

	private boolean isCastedValueEqual(Object expectedValue,
			org.unitils.dbunit.dataset.Column actualColumn) {
		Object castedExpectedValue = getCastedValue(expectedValue,
				actualColumn.getType());
		Object actualValue = actualColumn.getValue();

		if (castedExpectedValue instanceof java.util.Date) {
			return isTemporalWithinLimit(castedExpectedValue, actualValue);
		}
		if (castedExpectedValue instanceof byte[]) {
			return Arrays.equals((byte[]) castedExpectedValue,
					(byte[]) actualValue);
		}
		return castedExpectedValue.equals(actualValue);
	}

	private ColumnDifference createDifferenceForCasted(Object expectedValue,
			org.unitils.dbunit.dataset.Column actualColumn) {
		Object castedExpectedValue = getCastedValue(expectedValue,
				actualColumn.getType());
		if (castedExpectedValue instanceof byte[]) {
			return new ColumnDifference(this,
					new org.unitils.dbunit.dataset.Column(
							actualColumn.getName(), actualColumn.getType(),
							Base64.encodeBase64String((byte[]) actualColumn
									.getValue())));
		}
		return new ColumnDifference(this, actualColumn);
	}

	private boolean isTemporalWithinLimit(Object castedExpectedValue,
			Object actualValue) {

		// Fix NPE
		if (null == actualValue) {
			return false;
		}

		long expectedMillis = ((java.util.Date) castedExpectedValue).getTime();
		long actualMillis = ((java.util.Date) actualValue).getTime();
		long difference = Math.abs(actualMillis - expectedMillis);
		return difference <= timeDifferenceLimit;
	}

	private Object getCastedValue(Object expectedValue, DataType castType) {
		// convert java.sql.Date to SQL TIME
		// always return midnight: 00:00:00
		if (expectedValue instanceof Date && DataType.TIME == castType) {
			return new Time(new DateMidnight(1970, 1, 1).getMillis());
		}
		// convert java.sql.Time to SQL DATE
		// always return starting date of java.sql.Time: 1970-01-01
		if (expectedValue instanceof Time && DataType.DATE == castType) {
			return new Date(new DateMidnight(1970, 1, 1).getMillis());
		}
		// convert java.sql.Timestamp to SQL DATE
		// wipe out time info, only leaving date, yyyy-MM-dd
		if (expectedValue instanceof Timestamp && DataType.DATE == castType) {
			return new Date(new DateTime(expectedValue).toDateMidnight()
					.getMillis());
		}
		// convert java.sql.Timestamp to SQL TIME
		// set date to starting date of java.sql.Time, wipe out milliseconds,
		// only leaving time, HH:mm:ss
		if (expectedValue instanceof Timestamp && DataType.TIME == castType) {
			return new Time(new DateTime(expectedValue).withDate(1970, 1, 1)
					.withMillisOfSecond(0).getMillis());
		}
		try {
			return castType.typeCast(expectedValue);
		} catch (TypeCastException e) {
			throw new UnitilsException("Unable to convert \"" + expectedValue
					+ "\" to " + castType.toString() + ". Column name: "
					+ getName() + ", current type: " + getType().toString(), e);
		}
	}

	// beans and setters

	private VariableResolver variableResolver;

	/**
	 * Set variable resolver.
	 * 
	 * @param variableResolver
	 *            Variable resolver
	 */
	public void setVariableResolver(VariableResolver variableResolver) {
		this.variableResolver = variableResolver;
	}

	private long timeDifferenceLimit;

	/**
	 * Set time difference limit.
	 * 
	 * @param timeDifferenceLimit
	 */
	public void setTimeDifferenceLimit(long timeDifferenceLimit) {
		this.timeDifferenceLimit = timeDifferenceLimit;
	}

	private AutoValueGenerator autoValueGenerator;

	/**
	 * Set autoValueGenerator.
	 * 
	 * @param autoValueGenerator
	 */
	public void setAutoValueGenerator(AutoValueGenerator autoValueGenerator) {
		this.autoValueGenerator = autoValueGenerator;
	}

}
