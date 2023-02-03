
def function 
node('built-in') {
    copyArtifacts filter: 'lib.groovy', fingerprintArtifacts: true, flatten: true, projectName: 'ScriptForStartBuild'
    function = fileLoader.load('lib.groovy')
}
function.setScript(this)

def echoColourText(text, color){
    ansiColor('xterm') {
        echo('\u001B[' + color.toString() + 'm' + text + '\u001B[0m')
    }
}

def get_hash_files(repo){
    result = [:]
    dir(repo) {
        git(branch: 'feature/common_object', credentialsId: '87e46017-dc29-49da-b21b-30f47184962d', url: String.format('https://bitbucket.org/pharmsklad/%1$s.git', repo))
        text = readFile('spec/syntax/CommonObject.txt')
        files = text.split(System.lineSeparator())
        files.each{value->
            newValue = value.trim()
            if (fileExists(newValue)) {
                result.put(newValue, sha256(newValue))
            } else {
                echoColourText(String.format('file %1$s in %2$s not found', newValue, repo), 31)
                currentBuild.result = 'UNSTABLE'
            }
        }
    }
    return result
}

def compare_hash_files(repo1, files1, repo2, files2){
    result = [:]
    debug_result = []
    error_result = []

    files1.each{key, value->
        files2_value = files2.get(key)
        if (files2_value != null) {
            if (files2_value == value){
                debug_result.add(String.format('file %1$s in %2$s and %3$s equal', key, name1, name2))
            } else{
                error_result.add(String.format('file %1$s in %2$s and %3$s not equal', key, name1, name2))
            }    
        }   
    }
    result.put('debug', debug_result)
    result.put('error', error_result)
    return result
}

def check(map, debug){
    save_map_name = [:]
    value_save_map = '1'

    map.each{key1, value1->
        map.each{key2, value2->
            
            name1 = key1
            files1 = value1
            name2 = key2
            files2 = value2
            if (key1 < key2){
                name1 = key2
                files1 = value2
                name2 = key1
                files2 = value1
            }

            if (name1 != name2){
                                
                save_key = name1 + '###' + name2
                if (save_map_name.get(save_key) != value_save_map){
                    save_map_name.put(save_key, value_save_map)
                    
                    echo_text = compare_hash_files(name1, files1, name2, files2)
                    
                    error_result = echo_text.get('error') 
                    if (error_result.size() > 0){
                        currentBuild.result = 'UNSTABLE'
                        error_result.each{text->
                            echoColourText(text, 35)
                        }
                    }
                    if (debug){
                        debug_result = echo_text.get('debug') 
                        debug_result.each{text->
                            echoColourText(text, 34)
                        }
                    }
                }               
            
            }

        }
    }
}

def add_repo(map, repo){
    stage(repo){
        map.put(repo, get_hash_files(repo))
    }
}

node('w-test'){
    debug = false

    function.sendMsg(true)
    currentBuild.result = "SUCCESS"

    try{
        
        deleteDir() 
        files_for_check = [:]
        
        add_repo(files_for_check, 'rtl-trade')
        add_repo(files_for_check, 'rtl-mdlp')
        add_repo(files_for_check, 'rtl-retail')

        stage('check'){
            check(files_for_check, debug)
        }

    } catch (exception) {
        function.setResultAfterError(exception)
    } finally {
        function.sendMsg(false)
    }
}
