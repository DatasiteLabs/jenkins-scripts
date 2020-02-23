def returnList = []
returnList << Jenkins.instance.getVersion()

def plugins = Jenkins.instance.pluginManager.plugins.toSorted()

plugins.each{
  plugin ->
  def attrs = plugin.getManifest().mainAttributes
  returnList << "dependency \'${attrs.getValue('Group-Id')}:${attrs.getValue('Short-Name')}:${attrs.getValue('Plugin-Version')}\'"
}
return returnList.join('\n').toString()
