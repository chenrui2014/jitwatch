/*
 * Copyright (c) 2013, 2014 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.util;

import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_ARGUMENTS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_HOLDER;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_NAME;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.ATTR_RETURN;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_CLOSE_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_DOT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OBJECT_REF;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_SQUARE_BRACKET;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_QUOTE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_SEMICOLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_SLASH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_SPACE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEBUG_LOGGING;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEBUG_LOGGING_OVC;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEBUG_LOGGING_SIG_MATCH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_ARRAY_BRACKET_PAIR;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_CLASS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_CLOSE_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_CLOSE_PARENTHESES;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_DOT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_EMPTY;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_ENTITY_GT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_ENTITY_LT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_NEWLINE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_OBJECT_ARRAY_DEF;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_OPEN_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_OPEN_PARENTHESES;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_PACKAGE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SEMICOLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SLASH;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SPACE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_VARARGS_DOTS;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adoptopenjdk.jitwatch.model.IMetaMember;
import org.adoptopenjdk.jitwatch.model.IParseDictionary;
import org.adoptopenjdk.jitwatch.model.IReadOnlyJITDataModel;
import org.adoptopenjdk.jitwatch.model.LogParseException;
import org.adoptopenjdk.jitwatch.model.MemberSignatureParts;
import org.adoptopenjdk.jitwatch.model.MetaClass;
import org.adoptopenjdk.jitwatch.model.PackageManager;
import org.adoptopenjdk.jitwatch.model.Tag;
import org.adoptopenjdk.jitwatch.model.bytecode.BytecodeInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ParseUtil
{
	private static final Logger logger = LoggerFactory.getLogger(ParseUtil.class);

	// class<SPACE>METHOD<SPACE>(PARAMS)RETURN

	//http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.2
	public static String CLASS_NAME_REGEX_GROUP = "([^;\\[/<>]+)";
	public static String METHOD_NAME_REGEX_GROUP = "([^;\\[/]+)";

	public static String PARAM_REGEX_GROUP = "(\\(.*\\))";
	public static String RETURN_REGEX_GROUP = "(.*)";

	private static final Pattern PATTERN_LOG_SIGNATURE = Pattern.compile("^" + CLASS_NAME_REGEX_GROUP + " "
			+ METHOD_NAME_REGEX_GROUP + " " + PARAM_REGEX_GROUP + RETURN_REGEX_GROUP);

	public static final String NAME_SHORT = "short";
	public static final String NAME_CHARACTER = "char";
	public static final String NAME_BYTE = "byte";
	public static final String NAME_LONG = "long";
	public static final String NAME_DOUBLE = "double";
	public static final String NAME_BOOLEAN = "boolean";
	public static final String NAME_INTEGER = "int";
	public static final String NAME_FLOAT = "float";
	public static final String NAME_VOID = "void";

	public static final char TYPE_SHORT = 'S';
	public static final char TYPE_CHARACTER = 'C';
	public static final char TYPE_BYTE = 'B';
	public static final char TYPE_VOID = 'V';
	public static final char TYPE_LONG = 'J';
	public static final char TYPE_DOUBLE = 'D';
	public static final char TYPE_BOOLEAN = 'Z';
	public static final char TYPE_INTEGER = 'I';
	public static final char TYPE_FLOAT = 'F';

	private ParseUtil()
	{
	}

	public static long parseStamp(String stamp)
	{
		long result = 0;

		if (stamp != null)
		{
			double number = parseLocaleSafeDouble(stamp);

			result = (long) (number * 1000);
		}
		else
		{
			logger.warn("Could not parse null stamp");
		}

		return result;
	}

	public static double parseLocaleSafeDouble(String str)
	{
		NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());

		double result = 0;

		try
		{
			result = nf.parse(str).doubleValue();
		}
		catch (ParseException pe)
		{
			logger.error("", pe);
		}

		return result;
	}

	public static Class<?> getPrimitiveClass(char c)
	{
		switch (c)
		{
		case TYPE_SHORT:
			return Short.TYPE;
		case TYPE_CHARACTER:
			return Character.TYPE;
		case TYPE_BYTE:
			return Byte.TYPE;
		case TYPE_VOID:
			return Void.TYPE;
		case TYPE_LONG:
			return Long.TYPE;
		case TYPE_DOUBLE:
			return Double.TYPE;
		case TYPE_BOOLEAN:
			return Boolean.TYPE;
		case TYPE_INTEGER:
			return Integer.TYPE;
		case TYPE_FLOAT:
			return Float.TYPE;
		}

		throw new RuntimeException("Unknown class for " + c);
	}

	/*
	 * [C => char[] [[I => int[][] [Ljava.lang.Object; => java.lang.Object[]
	 */
	public static String expandParameterType(String name)
	{
		StringBuilder builder = new StringBuilder();

		int arrayDepth = 0;
		int pos = 0;

		outerloop: while (pos < name.length())
		{
			char c = name.charAt(pos);

			switch (c)
			{
			case C_OPEN_SQUARE_BRACKET:
				arrayDepth++;
				break;
			case TYPE_SHORT:
				builder.append(NAME_SHORT);
				break;
			case TYPE_CHARACTER:
				builder.append(NAME_CHARACTER);
				break;
			case TYPE_BYTE:
				builder.append(NAME_BYTE);
				break;
			case TYPE_LONG:
				builder.append(NAME_LONG);
				break;
			case TYPE_DOUBLE:
				builder.append(NAME_DOUBLE);
				break;
			case TYPE_BOOLEAN:
				builder.append(NAME_BOOLEAN);
				break;
			case TYPE_INTEGER:
				builder.append(NAME_INTEGER);
				break;
			case TYPE_FLOAT:
				builder.append(NAME_FLOAT);
				break;
			case C_SEMICOLON:
				break;
			default:
				if (name.charAt(pos) == C_OBJECT_REF && name.endsWith(S_SEMICOLON))
				{
					builder.append(name.substring(pos + 1, name.length() - 1));
				}
				else
				{
					builder.append(name.substring(pos));
				}
				break outerloop;
			}

			pos++;
		}

		for (int i = 0; i < arrayDepth; i++)
		{
			builder.append(S_ARRAY_BRACKET_PAIR);
		}

		return builder.toString();
	}

	public static String[] splitLogSignatureWithRegex(final String logSignature) throws LogParseException
	{
		String sig = logSignature;

		sig = sig.replace(S_ENTITY_LT, S_OPEN_ANGLE);
		sig = sig.replace(S_ENTITY_GT, S_CLOSE_ANGLE);

		Matcher matcher = PATTERN_LOG_SIGNATURE.matcher(sig);

		if (matcher.find())
		{
			String className = matcher.group(1);
			String methodName = matcher.group(2);
			String paramTypes = matcher.group(3).replace(S_OPEN_PARENTHESES, S_EMPTY).replace(S_CLOSE_PARENTHESES, S_EMPTY);
			String returnType = matcher.group(4);

			return new String[] { className, methodName, paramTypes, returnType };
		}

		throw new LogParseException("Could not split signature with regex: '" + logSignature + C_QUOTE);
	}

	public static IMetaMember findMemberWithSignature(IReadOnlyJITDataModel model, String logSignature) throws LogParseException
	{
		IMetaMember metaMember = null;

		if (logSignature != null)
		{
			MemberSignatureParts msp = MemberSignatureParts.fromLogCompilationSignature(logSignature);

			metaMember = model.findMetaMember(msp);

			if (metaMember == null)
			{
				throw new LogParseException("MetaMember not found for " + logSignature);
			}
		}

		return metaMember;
	}

	public static Class<?>[] getClassTypes(String typesString) throws LogParseException
	{
		List<Class<?>> classes = new ArrayList<Class<?>>();

		if (typesString.length() > 0)
		{
			try
			{
				findClassesForTypeString(typesString, classes);
			}
			catch (Throwable t)
			{
				throw new LogParseException("Could not parse types: " + typesString, t);
			}

		} // end if empty

		return classes.toArray(new Class<?>[classes.size()]);
	}

	public static Class<?> findClassForLogCompilationParameter(String param) throws ClassNotFoundException
	{
		StringBuilder builder = new StringBuilder();

		if (isPrimitive(param))
		{
			return classForPrimitive(param);
		}
		else
		{
			int arrayBracketCount = getArrayBracketCount(param);

			if (param.contains(S_CLOSE_ANGLE))
			{
				param = stripGenerics(param);
			}

			if (arrayBracketCount == 0)
			{
				if (param.endsWith(S_VARARGS_DOTS))
				{
					String partBeforeDots = param.substring(0, param.length() - S_VARARGS_DOTS.length());

					if (isPrimitive(partBeforeDots))
					{
						builder.append(S_OPEN_ANGLE).append(classForPrimitive(partBeforeDots));
					}
					else
					{
						builder.append(S_OBJECT_ARRAY_DEF).append(partBeforeDots);
						builder.append(C_SEMICOLON);
					}
				}
				else
				{
					builder.append(param);
				}
			}
			else
			{
				int arrayBracketChars = 2 * arrayBracketCount;

				String partBeforeArrayBrackets = param.substring(0, param.length() - arrayBracketChars);

				for (int i = 0; i < arrayBracketCount - 1; i++)
				{
					builder.append(C_OPEN_SQUARE_BRACKET);
				}

				if (isPrimitive(partBeforeArrayBrackets))
				{
					builder.append(C_OPEN_SQUARE_BRACKET);

					builder.append(getClassTypeCharForPrimitiveTypeString(partBeforeArrayBrackets));
				}
				else
				{
					builder.append(S_OBJECT_ARRAY_DEF);

					builder.append(param);

					builder.delete(builder.length() - arrayBracketChars, builder.length());

					builder.append(C_SEMICOLON);
				}
			}

			return ClassUtil.loadClassWithoutInitialising(builder.toString());
		}
	}

	public static String stripGenerics(String param)
	{
		String result = param;

		if (param != null)
		{
			int firstOpenAngle = param.indexOf(C_OPEN_ANGLE);
			int lastCloseAngle = param.lastIndexOf(C_CLOSE_ANGLE);

			if (firstOpenAngle != -1 && lastCloseAngle != -1 && firstOpenAngle < lastCloseAngle)
			{
				result = param.substring(0, firstOpenAngle) + param.substring(lastCloseAngle + 1);
			}
		}

		return result;
	}

	public static boolean paramClassesMatch(boolean memberHasVarArgs, List<Class<?>> memberParamClasses,
			List<Class<?>> signatureParamClasses, boolean matchTypesExactly)
	{
		boolean result = true;

		final int memberParamCount = memberParamClasses.size();
		final int signatureParamCount = signatureParamClasses.size();

		if (DEBUG_LOGGING_SIG_MATCH)
		{
			logger.debug("MemberParamCount:{} SignatureParamCount:{} varArgs:{}", memberParamCount, signatureParamCount,
					memberHasVarArgs);
		}

		if (!memberHasVarArgs && memberParamCount != signatureParamCount)
		{
			result = false;
		}
		else if (memberParamCount > 0 && signatureParamCount > 0)
		{
			int memPos = memberParamCount - 1;

			for (int sigPos = signatureParamCount - 1; sigPos >= 0; sigPos--)
			{
				Class<?> sigParamClass = signatureParamClasses.get(sigPos);

				Class<?> memParamClass = memberParamClasses.get(memPos);

				boolean memberParamCouldBeVarArgs = false;

				boolean isLastParameter = (memPos == memberParamCount - 1);

				if (memberHasVarArgs && isLastParameter)
				{
					memberParamCouldBeVarArgs = true;
				}

				boolean classMatch = false;

				if (matchTypesExactly)
				{
					classMatch = memParamClass.equals(sigParamClass);
				}
				else
				{
					classMatch = memParamClass.isAssignableFrom(sigParamClass);
				}

				if (classMatch)
				{
					if (DEBUG_LOGGING_SIG_MATCH)
					{
						logger.debug("{} equals/isAssignableFrom {}", memParamClass, sigParamClass);
					}

					if (memPos > 0)
					{
						// move to previous member parameter
						memPos--;
					}
					else if (sigPos > 0)
					{
						if (DEBUG_LOGGING_SIG_MATCH)
						{
							logger.debug("More signature params but no more member params to try");
						}

						result = false;
						break;
					}
				}
				else
				{
					if (memberParamCouldBeVarArgs)
					{
						// check assignable
						Class<?> componentType = memParamClass.getComponentType();

						if (!componentType.isAssignableFrom(sigParamClass))
						{
							result = false;
							break;
						}
					}
					else
					{
						result = false;
						break;
					}

				} // if classMatch

			} // for

			boolean unusedMemberParams = (memPos > 0);

			if (unusedMemberParams)
			{
				result = false;
			}
		}

		return result;
	}

	public static boolean typeIsVarArgs(String type)
	{
		return type != null && type.endsWith(S_VARARGS_DOTS);
	}

	public static char getClassTypeCharForPrimitiveTypeString(String type)
	{
		switch (type)
		{
		case NAME_INTEGER:
			return TYPE_INTEGER;
		case NAME_BOOLEAN:
			return TYPE_BOOLEAN;
		case NAME_LONG:
			return TYPE_LONG;
		case NAME_DOUBLE:
			return TYPE_DOUBLE;
		case NAME_FLOAT:
			return TYPE_FLOAT;
		case NAME_SHORT:
			return TYPE_SHORT;
		case NAME_BYTE:
			return TYPE_BYTE;
		case NAME_CHARACTER:
			return TYPE_CHARACTER;
		case NAME_VOID:
			return TYPE_VOID;
		}

		throw new RuntimeException(type + " is not a primitive type");
	}

	public static boolean isPrimitive(String type)
	{
		boolean result = false;

		if (type != null)
		{
			switch (type)
			{
			case NAME_INTEGER:
			case NAME_BOOLEAN:
			case NAME_LONG:
			case NAME_DOUBLE:
			case NAME_FLOAT:
			case NAME_SHORT:
			case NAME_BYTE:
			case NAME_CHARACTER:
			case NAME_VOID:
				result = true;
			}
		}

		return result;
	}

	public static Class<?> classForPrimitive(String primitiveType)
	{
		if (primitiveType != null)
		{
			switch (primitiveType)
			{
			case NAME_INTEGER:
				return int.class;
			case NAME_BOOLEAN:
				return boolean.class;
			case NAME_LONG:
				return long.class;
			case NAME_DOUBLE:
				return double.class;
			case NAME_FLOAT:
				return float.class;
			case NAME_SHORT:
				return short.class;
			case NAME_BYTE:
				return byte.class;
			case NAME_CHARACTER:
				return char.class;
			case NAME_VOID:
				return void.class;
			}
		}

		throw new RuntimeException(primitiveType + " is not a primitive type");
	}

	public static int getArrayBracketCount(String param)
	{
		int count = 0;

		if (param != null)
		{
			int index = param.indexOf(S_ARRAY_BRACKET_PAIR, 0);

			while (index != -1)
			{
				count++;

				index = param.indexOf(S_ARRAY_BRACKET_PAIR, index + 2);
			}
		}

		return count;
	}

	/*
	 * Converts (III[Ljava.lang.String;) into a list of Class<?>
	 */
	private static void findClassesForTypeString(final String typesString, List<Class<?>> classes) throws ClassNotFoundException
	{
		// logger.debug("Parsing: {}", typesString);

		String toParse = typesString.replace(C_SLASH, C_DOT);

		int pos = 0;

		StringBuilder builder = new StringBuilder();

		final int stringLen = toParse.length();

		while (pos < stringLen)
		{
			char c = toParse.charAt(pos);

			switch (c)
			{
			case C_OPEN_SQUARE_BRACKET:
				// Could be
				// [Ljava.lang.String; Object array
				// [I primitive array
				// [..[I multidimensional primitive array
				// [..[Ljava.lang.String multidimensional Object array
				builder.delete(0, builder.length());
				builder.append(c);
				pos++;
				c = toParse.charAt(pos);

				while (c == C_OPEN_SQUARE_BRACKET)
				{
					builder.append(c);
					pos++;
					c = toParse.charAt(pos);
				}

				if (c == C_OBJECT_REF)
				{
					// array of ref type
					while (pos < stringLen)
					{
						c = toParse.charAt(pos++);
						builder.append(c);

						if (c == C_SEMICOLON)
						{
							break;
						}
					}
				}
				else
				{
					// array of primitive
					builder.append(c);
					pos++;
				}

				Class<?> arrayClass = ClassUtil.loadClassWithoutInitialising(builder.toString());
				classes.add(arrayClass);
				builder.delete(0, builder.length());
				break;
			case C_OBJECT_REF:
				// ref type
				while (pos < stringLen - 1)
				{
					pos++;
					c = toParse.charAt(pos);

					if (c == C_SEMICOLON)
					{
						pos++;
						break;
					}

					builder.append(c);
				}
				Class<?> refClass = ClassUtil.loadClassWithoutInitialising(builder.toString());
				classes.add(refClass);
				builder.delete(0, builder.length());
				break;
			default:
				// primitive
				Class<?> primitiveClass = ParseUtil.getPrimitiveClass(c);
				classes.add(primitiveClass);
				pos++;

			} // end switch

		} // end while
	}

	public static String findBestMatchForMemberSignature(IMetaMember member, List<String> lines)
	{
		String match = null;

		logger.debug("findBestMatch: {}", member.toString());

		if (lines != null)
		{
			int index = findBestLineMatchForMemberSignature(member, lines);

			if (index > 0 && index < lines.size())
			{
				match = lines.get(index);
			}
		}

		return match;
	}

	public static int findBestLineMatchForMemberSignature(IMetaMember member, List<String> lines)
	{
		int bestScoreLine = 0;

		if (lines != null)
		{
			String memberName = member.getMemberName();
			int modifier = member.getModifier();
			String returnTypeName = member.getReturnTypeName();
			String[] paramTypeNames = member.getParamTypeNames();

			int bestScore = 0;

			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);

				int score = 0;

				if (line.contains(memberName))
				{
					if (DEBUG_LOGGING_SIG_MATCH)
					{
						logger.debug("Comparing {} with {}", line, member);
					}

					MemberSignatureParts msp = MemberSignatureParts.fromBytecodeSignature(member.getMetaClass()
							.getFullyQualifiedName(), line);

					if (!memberName.equals(msp.getMemberName()))
					{
						continue;
					}

					// modifiers matched
					if (msp.getModifier() != modifier)
					{
						continue;
					}

					List<String> mspParamTypes = msp.getParamTypes();

					if (mspParamTypes.size() != paramTypeNames.length)
					{
						continue;
					}

					int pos = 0;

					for (String memberParamType : paramTypeNames)
					{
						String mspParamType = msp.getParamTypes().get(pos++);

						if (compareTypeEquality(memberParamType, mspParamType, msp.getGenerics()))
						{
							score++;
						}
					}

					// return type matched
					if (compareTypeEquality(returnTypeName, msp.getReturnType(), msp.getGenerics()))
					{
						score++;
					}

					if (score > bestScore)
					{
						bestScoreLine = i;
						bestScore = score;
					}
				}
			}
		}

		return bestScoreLine;
	}

	private static boolean compareTypeEquality(String memberTypeName, String inMspTypeName, Map<String, String> genericsMap)
	{
		String mspTypeName = inMspTypeName;
		if (memberTypeName != null && memberTypeName.equals(mspTypeName))
		{
			return true;
		}
		else if (mspTypeName != null)
		{
			// Substitute generics to match with non-generic signature
			// public static <T extends java.lang.Object, U extends
			// java.lang.Object> T[] copyOf(U[], int, java.lang.Class<? extends
			// T[]>)";
			// U[] -> java.lang.Object[]
			String mspTypeNameWithoutArray = getParamTypeWithoutArrayBrackets(mspTypeName);
			String genericSubstitution = genericsMap.get(mspTypeNameWithoutArray);

			if (genericSubstitution != null)
			{
				mspTypeName = mspTypeName.replace(mspTypeNameWithoutArray, genericSubstitution);

				if (memberTypeName != null && memberTypeName.equals(mspTypeName))
				{
					return true;
				}
			}
		}

		return false;
	}

	public static String getParamTypeWithoutArrayBrackets(String paramType)
	{
		int bracketsIndex = paramType.indexOf(S_ARRAY_BRACKET_PAIR);

		if (bracketsIndex != -1)
		{
			return paramType.substring(0, bracketsIndex);
		}
		else
		{
			return paramType;
		}
	}

	public static String getMethodTagReturn(Tag methodTag, IParseDictionary parseDictionary)
	{
		String returnTypeId = methodTag.getAttribute(ATTR_RETURN);

		String returnType = lookupType(returnTypeId, parseDictionary);

		return returnType;
	}

	public static List<String> getMethodTagArguments(Tag methodTag, IParseDictionary parseDictionary)
	{
		List<String> result = new ArrayList<>();

		String argumentsTypeId = methodTag.getAttribute(ATTR_ARGUMENTS);

		if (argumentsTypeId != null)
		{
			String[] typeIDs = argumentsTypeId.split(S_SPACE);

			for (String typeID : typeIDs)
			{
				result.add(lookupType(typeID, parseDictionary));
			}
		}

		return result;
	}

	public static IMetaMember lookupMember(String methodId, IParseDictionary parseDictionary, IReadOnlyJITDataModel model)
	{
		IMetaMember result = null;

		Tag methodTag = parseDictionary.getMethod(methodId);

		if (methodTag != null)
		{
			String methodName = methodTag.getAttribute(ATTR_NAME);

			String klassId = methodTag.getAttribute(ATTR_HOLDER);

			Tag klassTag = parseDictionary.getKlass(klassId);

			String metaClassName = klassTag.getAttribute(ATTR_NAME);

			metaClassName = metaClassName.replace(S_SLASH, S_DOT);

			String returnType = getMethodTagReturn(methodTag, parseDictionary);

			List<String> argumentTypes = getMethodTagArguments(methodTag, parseDictionary);

			PackageManager pm = model.getPackageManager();

			MetaClass metaClass = pm.getMetaClass(metaClassName);

			if (metaClass == null)
			{
				if (DEBUG_LOGGING)
				{
					logger.debug("metaClass not found: {}. Attempting classload", metaClassName);
				}

				// Possible that TraceClassLoading did not log this class
				// try to classload and add to model

				Class<?> clazz = null;

				try
				{
					clazz = ClassUtil.loadClassWithoutInitialising(metaClassName);

					if (clazz != null)
					{
						metaClass = model.buildAndGetMetaClass(clazz);
					}
				}
				catch (ClassNotFoundException cnf)
				{
					logger.error("ClassNotFoundException: '" + metaClassName + C_QUOTE);
				}
				catch (NoClassDefFoundError ncdf)
				{
					logger.error("NoClassDefFoundError: '" + metaClassName + C_SPACE + ncdf.getMessage() + C_QUOTE);
				}
			}

			if (metaClass != null)
			{
				MemberSignatureParts msp = MemberSignatureParts.fromParts(metaClass.getFullyQualifiedName(), methodName,
						returnType, argumentTypes);
				result = metaClass.getMemberForSignature(msp);
			}
			else
			{
				logger.error("metaClass not found: {}", metaClassName);
			}
		}

		return result;
	}

	public static String lookupType(String typeOrKlassID, IParseDictionary parseDictionary)
	{
		String result = null;

		// logger.debug("Looking up type: {}", typeOrKlassID);

		if (typeOrKlassID != null)
		{
			Tag typeTag = parseDictionary.getType(typeOrKlassID);

			// logger.debug("Type? {}", typeTag);

			if (typeTag == null)
			{
				typeTag = parseDictionary.getKlass(typeOrKlassID);

				// logger.debug("Klass? {}", typeTag);
			}

			if (typeTag != null)
			{
				String typeAttrName = typeTag.getAttribute(ATTR_NAME);

				// logger.debug("Name {}", typeAttrName);

				if (typeAttrName != null)
				{
					result = typeAttrName.replace(S_SLASH, S_DOT);

					result = ParseUtil.expandParameterType(result);
				}
			}
		}

		return result;
	}

	public static String getPackageFromSource(String source)
	{
		String result = null;

		String[] lines = source.split(S_NEWLINE);

		for (String line : lines)
		{
			line = line.trim();

			if (line.startsWith(S_PACKAGE) && line.endsWith(S_SEMICOLON))
			{
				result = line.substring(S_PACKAGE.length(), line.length() - 1).trim();
			}
		}

		if (result == null)
		{
			result = S_EMPTY;
		}

		return result;
	}

	public static String getClassFromSource(String source)
	{
		String result = null;

		String[] lines = source.split(S_NEWLINE);

		String classToken = S_SPACE + S_CLASS + S_SPACE;

		for (String line : lines)
		{
			line = line.trim();

			int classTokenPos = line.indexOf(classToken);

			if (classTokenPos != -1)
			{
				result = line.substring(classTokenPos + classToken.length());
			}
		}

		if (result == null)
		{
			result = "";
		}

		return result;
	}

	private static boolean commentMethodHasNoClassPrefix(String comment)
	{
		return (comment.indexOf(C_DOT) == -1);
	}

	private static String prependCurrentMember(String comment, IMetaMember member)
	{
		String currentClass = member.getMetaClass().getFullyQualifiedName();

		currentClass = currentClass.replace(C_DOT, C_SLASH);

		return currentClass + C_DOT + comment;
	}

	public static IMetaMember getMemberFromBytecodeComment(IReadOnlyJITDataModel model, IMetaMember currentMember,
			BytecodeInstruction instruction) throws LogParseException
	{
		IMetaMember result = null;

		if (DEBUG_LOGGING_OVC)
		{
			logger.debug("Looking for member in {} using {}", currentMember, instruction);
		}

		if (instruction != null)
		{
			String comment = instruction.getCommentWithMemberPrefixStripped();

			if (comment != null)
			{
				if (commentMethodHasNoClassPrefix(comment) && currentMember != null)
				{
					comment = prependCurrentMember(comment, currentMember);
				}

				MemberSignatureParts msp = MemberSignatureParts.fromBytecodeComment(comment);

				result = model.findMetaMember(msp);
			}
		}

		return result;
	}
}
