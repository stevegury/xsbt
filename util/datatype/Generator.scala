package xsbt.api

import java.io.File
import xsbt.FileUtilities

class Generator(pkgName: String, baseDirectory: File)
{
	def writeDefinitions(ds: Iterable[Definition]) =
	{
		val (nameSet, duplicates) =
			( (Set[String](), Set[String]()) /: ds.map(_.name)) {
				case ((nameSet, duplicates), name) =>
					if(nameSet.contains(name)) (nameSet, duplicates + name) else (nameSet + name, duplicates)
			}
		if(duplicates.isEmpty)
			ds.foreach(writeDefinition)
		else
			error("Duplicate names:\n\t" + duplicates.mkString("\n\t"))
	}
	def writeDefinition(d: Definition) = d match { case e: EnumDef => write(e); case c: ClassDef => write(c) }
	def write(e: EnumDef)
	{
		val content =
			"public enum " + e.name + " {" +
				e.members.mkString("\n\t", ",\n\t", "\n") +
			"}"
		writeSource(e.name, content)
	}
	def write(c: ClassDef): Unit = writeSource(c.name, classContent(c))
	def classContent(c: ClassDef): String =
	{
		val allMembers = c.allMembers.map(normalize)
		val normalizedMembers = c.members.map(normalize)
		val fields = normalizedMembers.map(m => "private final " + m.asJavaDeclaration + ";")
		val accessors = normalizedMembers.map(m => "public final " + m.asJavaDeclaration + "() \n\t{\n\t\treturn " + m.name + ";\n\t}")
		val parameters = allMembers.map(_.asJavaDeclaration)
		val assignments = normalizedMembers.map(m => "this." + m.name + " = " + m.name + ";")
		val superConstructor =
		{
			val inherited = c.inheritedMembers
			if(inherited.isEmpty) "" else  "super(" + inherited.map(_.name).mkString(", ") + ");\n\t\t"
		}
		val parametersString = if(allMembers.isEmpty) "\"\"" else allMembers.map(m => fieldToString(m.name, m.single)).mkString(" + \", \" + ")
		val toStringMethod = "public String toString()\n\t{\n\t\t" +
				"return \"" + c.name + "(\" + " + parametersString + "+ \")\";\n\t" +
			"}\n"

		val constructor = "public " + c.name + "("  + parameters.mkString(", ") + ")\n\t" +
		"{\n\t\t" +
			superConstructor +
			assignments.mkString("\n\t\t") + "\n\t" +
		"}"
		"\nimport java.util.Arrays;\n" +
		"public class " + c.name + c.parent.map(" extends " + _.name + " ").getOrElse("") + "\n" +
		"{\n\t" +
			constructor + "\n\t" + 
			(fields ++ accessors).mkString("\n\t") + "\n\t" +
			toStringMethod + "\n" +
		"}"
	}
	def fieldToString(name: String, single: Boolean) = "\"" + name + ": \" + " + fieldString(name + "()", single)
	def fieldString(arg: String, single: Boolean) = if(single) arg else "Arrays.toString(" + arg + ")"
	def normalize(m: MemberDef): MemberDef =
		m.mapType(tpe => if(primitives(tpe.toLowerCase)) tpe.toLowerCase else tpe)

	def writeSource(name: String, content: String)
	{
		import Paths._
		val file =baseDirectory / packagePath / (name+ ".java")
		file.getParentFile.mkdirs()
		FileUtilities.write(file, "package " + pkgName + ";\n\n" + content)
	}
	private def packagePath = pkgName.replace('.', File.separatorChar)
	private val primitives = Set("int", "boolean", "float", "long", "short", "byte", "char", "double")
}