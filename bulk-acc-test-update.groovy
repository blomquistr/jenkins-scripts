/**
 * This job will update all of the matching acceptance test jobs in the related folder,
 * which includes both the env property groovy script and the build shell command
 *
 * -> Tests/AcceptanceTests/DevAcceptanceTests
 * -> Tests/AcceptanceTests/StageAcceptanceTests
 * -> Tests/AcceptanceTests/ProdUsAcceptanceTests
 * -> Tests/AcceptanceTests/ProdEuAcceptanceTests
 */

import jenkins.model.Jenkins
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo

populateJobParameters()

node('java') {
    stage('Update Acc Tests') {
        currentBuild.setDescription("update $targetEnvironment acc tests")
        def pcfEnvironment = pcfEnvironment()
        def accTestFolderName = buildAccTestFolderName()
        def accTestFolder = Jenkins.instance.getItemByFullName(accTestFolderName)
        accTestFolder.items.each { job -> updateAccTestJob(job, pcfEnvironment) }
    }
}

def buildAccTestFolderName() {
    "Javelin/Tests/AcceptanceTests/${targetFolder}AcceptanceTests"
}

def updateAccTestJob(job, pcfEnvironment) {
    def shellCommand = getShellCommand(job)
    if (shellCommand == null) {
        println "ERROR: job ${job.name} has no shell command"
        return false
    }
    stopRunningLastBuild(job)
    def serviceUrl = updateShellCommand(job, shellCommand, pcfEnvironment)
    updateGroovyScript(job, serviceUrl)
    return true
}

def getGroovyScript(job) {
    def groovyScript = null
    job.properties.each { propDesc, propClass ->
        propClass.each {
            if (it.class.name.contains("EnvInjectJobProperty")) {
                groovyScript = it.info.groovyScriptContent
            }
        }
    }
    groovyScript
}

def setGroovyScript(job, newGroovyScript) {
    job.properties.each { propDesc, propClass ->
        propClass.each {
            if (it.class.name.contains("EnvInjectJobProperty")) {
                def propInfo = new EnvInjectJobPropertyInfo(
                        null, null, null, null, newGroovyScript, false)
                it.setInfo(propInfo)
            }
        }
    }
}

def updateGroovyScript(job, serviceUrl) {
    def groovyScript = getGroovyScript(job)
    if (groovyScript) {
        println "updating groovy script for job ${job.name}"
        def newGroovyScript = buildGroovyScript(serviceUrl)
        setGroovyScript(job, newGroovyScript)
        job.save()
    }
}

def buildGroovyScript(serviceUrl) {
    """URLConnection reposConn = new URL("${serviceUrl}/info").openConnection()
def jsonResult = jsonParse(new BufferedReader(new InputStreamReader(reposConn.getInputStream())))
currentCommit = jsonResult.git.commit.id
return [COMMIT: currentCommit]
def jsonParse(Reader reader) {
new groovy.json.JsonSlurperClassic().parse(reader)
}
"""
}

def getShellCommand(job) {
    def shellCommand = null
    job.builders.each { builder ->
        builder.each {
            if (it.command.contains("TARGET_SERVICE")) {
                shellCommand = it.command
            }
        }
    }
    shellCommand
}

def setShellCommand(job, newShellCommand) {
    job.builders.each { builder ->
        builder.each {
            if (it.command.contains("TARGET_SERVICE")) {
                def newBuilder = new hudson.tasks.Shell(newShellCommand)
                job.buildersList.add(newBuilder)
                job.buildersList.remove(builder)
            }
        }
    }
}

def updateShellCommand(job, shellCommand, pcfEnvironment) {
    println "updating shell command for job ${job.name}"
    def shellCommandMap = buildShellCommandAndServiceUrl(shellCommand, pcfEnvironment)
    setShellCommand(job, shellCommandMap['shellCommand'])
    job.save()
    shellCommandMap['serviceUrl']
}

def buildShellCommandAndServiceUrl(shellCommand, pcfEnvironment) {
    def targetService = buildTargetService(shellCommand)
    def appDomain = "apps.${pcfEnvironment.domain}"
    def apiDomain = "api.sys.${pcfEnvironment.domain}"
    def serviceUrl = "http://${targetService}${pcfEnvironment.appSuffix}.${appDomain}"
    def plainEnvironment = targetEnvironment.replaceFirst(/[bg]$/, "")
    def newShellCommand = "./gradlew clean runAcceptance"
    newShellCommand += " -DPCF_PWD=Hcrx2WYHO!o"
    newShellCommand += " -DPCF_SPACE=${pcfEnvironment.space}"
    newShellCommand += " -DPCF_ORG=${pcfEnvironment.organization}"
    newShellCommand += " -DPCF_URI=${apiDomain}"
    newShellCommand += " -DPCF_API_DOMAIN=${apiDomain}"
    newShellCommand += " -DTARGET_DOMAIN=${appDomain}"
    newShellCommand += " -DTARGET_ENVIRONMENT=${plainEnvironment}"
    newShellCommand += " -DTARGET_SERVICE=${targetService}"
    [shellCommand: newShellCommand, serviceUrl: serviceUrl]
}

def buildTargetService(shellCommand) {
    def targetService = "TARGET_SERVICE="
    def targetServiceIndex = shellCommand.indexOf(targetService)
    def targetServiceNameInMiddleIndex = shellCommand.indexOf(" ", targetServiceIndex + targetService.length())
    if (targetServiceNameInMiddleIndex == -1) {
        return shellCommand.substring(targetServiceIndex + targetService.length())
    }
    shellCommand.substring(targetServiceIndex + targetService.length(), targetServiceNameInMiddleIndex)
}

def stopRunningLastBuild(job) {
    def lastBuild = job.lastBuild
    if (!lastBuild || !lastBuild.building) {
        return
    }
    println "stopping last build for job ${job.name}"
    lastBuild.doStop()
    while (lastBuild.building) {
        Thread.sleep(2000)
    }
}

def pcfEnvironment() {
    def pcfEnvData = build job: 'Javelin/PCF-Upgrade/PCF-Environments'
    pcfEnvData.buildVariables[params.targetEnvironment][1..-2]
            .split(', ').collectEntries { entry ->
        def pair = entry.tokenize(':')
        def lastPair = (pair.first() != pair.last()) ? pair.last() : ''
        [(pair.first()): lastPair]
    }
}

def populateJobParameters() {
    def pcfEnvData = build job: 'Javelin/PCF-Upgrade/PCF-Environments'
    def pcfEnvironments = pcfEnvData.buildVariables.envNames[1..-2].split(', ')
    def targetFolders = ['Dev', 'Stage', 'ProdUs', 'ProdEu']
    properties([
            parameters([
                    choice(name: 'targetEnvironment', choices: pcfEnvironments.join('\n'), description: ''),
                    choice(name: 'targetFolder', choices: targetFolders.join('\n'), description: '')
            ])
    ])
}
