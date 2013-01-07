"""Usage: jelly2groovy INPUTFILE OUTPUTFILE
          

Convert INPUTFILE from a Jenkins Jelly view to a Jenkins Groovy view and 
write to OUTPUTFILE.

Arguments:
  INPUTFILE        the path to the Jelly file to convert.
  OUTPUTFILE       the path to the Groovy file to write to.

"""

import java.io.File

def convertValue(input) {
  res = input
  if( input.startsWith('${%') )
    res = "_(\"${input[3..-2]}\")"
  else if( input.startsWith('${') && input.endsWith('}') )
    res = input[2..-2]
  else
    res = "\"${input.trim()}\""
  return res
}

def parseAndGetNs(file) {
  def jelly = new XmlSlurper().parse(file)

  def jellyClass = jelly.getClass()
  def gpathClass = jellyClass.getSuperclass()
  def namespaceTagHintsField = gpathClass.getDeclaredField("namespaceTagHints")
  namespaceTagHintsField.setAccessible(true)

  def ns = namespaceTagHintsField.get(jelly)
  
  return [jelly, ns]
}

def localText(parent) {
  def children = parent.getAt(0).children
  def result = [] as List
  for(child in children) {
    if (!(child instanceof groovy.util.slurpersupport.Node)) {
      result.add(child)
    }
  }
  if(result.size() > 0) {
    return result[0]
  }
  return ""
}


def generateCode(elem, out, ns, indent) {
  prefix = ''
  tag = elem.name()
  jellyCore = false
  output = true

  if( elem.namespaceURI()) {
    if( elem.namespaceURI() == 'jelly:core' ) {
      jellyCore = true
    }

    prefix = ns.find{it.value == elem.namespaceURI() }?.key
    if(prefix) {
      prefix += "."
    }
  }

  if( !jellyCore ) {
    out.write("${'  ' * indent}${prefix}${tag}(")

    attributes = []
    elem.attributes().each { attributes << "${it.key}: ${convertValue(it.value)}" }

    text = localText(elem)
    
    if( !(text.isAllWhitespace()) ) {
      attributes << convertValue(text)
    }

    if( attributes.size() > 0 ) {
      out.write(attributes.join(', '))
    }

    out.write(') ')
  } else {
    if( tag == 'if' ) {
      out.write("${'  ' * indent}if(${convertValue(elem.attributes()['test'])}")
    } else if (tag == 'choose') {
      output = false
    } else if(tag == 'invokeStatic') {
      out.write("${'  ' * indent}def ${elem.attributes()['var']} = ${elem.attributes()['className']}.${elem.attributes()['method']}")
    } else if(tag == 'when') {
      out.write("${'  ' * indent}if(${convertValue(elem.attributes()['test'])})")
    } else if(tag == 'otherwise') {
      out.write("${'  ' * indent}else")
    }
  }
    
  if( output ) {
    if(elem.children().size() > 0) {
      out.write('{')
    }
    out.writeLine("")
  }  

  elem.children().each { generateCode(it, out, ns, indent + 1) }
  
  if( output && (elem.children().size() > 0) ) {
    out.writeLine("${'  ' * indent}}")
  }
}


inputfile = args[0]
outputfile = args[1]

def (jelly, ns) = parseAndGetNs(inputfile)

o = new File(outputfile).newWriter();

o.writeLine("// Namespaces")

ns.each{ k, v -> 
  if(k) {
    o.writeLine("${k} = namespace(\"${v}\")") 
  }
}

o.writeLine("")
o.writeLine("")

jelly.children().each { generateCode(it, o, ns, 0) }
  
o.close()