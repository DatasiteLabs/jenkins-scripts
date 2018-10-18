// TODO: output jenkisn dependencies, add any special cases
println Jenkins.instance.getVersion()

def plugins = Jenkins.instance.pluginManager.plugins.toSorted()

// maven
plugins.each{
  plugin ->
  def attrs = plugin.getManifest().mainAttributes
//  println "${plugin.getInfo().name},${plugin.getVersion()}, ${attrs.getValue('Group-Id')}"
  println "<dependency>"
  println "\t<groupId>${attrs.getValue('Group-Id')}</groupId>"
  println "\t<artifactId>${plugin.getInfo().name}</artifactId>"
  println "\t<version>${plugin.getVersion()}</version>"
  println "</dependency>"
  println ""
}