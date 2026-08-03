// Authored by HealCI. Review this before merging — it will run with whatever access this Jenkins has.
//
// Bind any secret with credentials('id'); never write one into this file. HealCI's scanner blocks a literal
// secret here, and a secret committed to a repository has to be treated as disclosed even after it is removed.
pipeline {
  // Runs on whatever agent Jenkins picks, so node must already be installed there.
  //
  // A container agent is stronger — it pins the toolchain to the pipeline instead of to the machine. It needs
  // the Docker Pipeline plugin (docker-workflow) and an agent that can run containers. With both, replace
  // `agent any` with:
  //
  //   agent {
  //     docker {
  //       image 'node:20-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293'
  //     }
  //   }
  agent any
  options {
    // A hung build holds an executor until somebody notices.
    timeout(time: 20, unit: 'MINUTES')
    // Unbounded history is how a controller fills its disk.
    buildDiscarder(logRotator(numToKeepStr: '30'))
    // Two builds of one job share one workspace. Without this they interleave and produce failures that look
    // like flaky tests. Core Jenkins — no plugin needed, unlike timestamps().
    disableConcurrentBuilds()
    // Keep the checkout out of the implicit first step so the Checkout stage below is the only place it happens.
    skipDefaultCheckout(true)
  }
  environment {
    // A secret belongs in a Jenkins credential, bound here by ID — never written into this file. Jenkins masks a
    // bound value in the build log; a value committed to a repository has to be treated as disclosed for ever.
    //
    //   MY_TOKEN = credentials('the-credential-id')   // Secret text
    //
    // For a username/password pair, wrap the step instead:
    //
    //   withCredentials([usernamePassword(credentialsId: 'id', usernameVariable: 'USER', passwordVariable: 'PASS')]) { … }
    //
    // Caches inside the workspace. A container agent runs as the agent's own uid, so a cache under $HOME is
    // usually not writable — and the widely-copied fix for that, args '-u root', runs the whole build as root
    // to solve a directory permission.
    npm_config_cache = "${WORKSPACE}/.npm"
  }
  stages {
    stage('Checkout') {
      steps {
        // `checkout scm` because this job is a *Pipeline script from SCM*: Jenkins already knows the repository
        // and the credential, having cloned it to read this very file. Naming them again here would be a second
        // source of truth that can disagree with the job — and on a multibranch project it would override the
        // branch Jenkins chose and build the wrong ref.
        //
        // Configured in the job as:  Definition = Pipeline script from SCM,  Script Path = jenkins/ci-pipeline.groovy
        checkout scm
      }
    }
    stage('Install') {
      steps {
        // Retried because a package registry timing out is not a broken build.
        retry(2) {
          sh 'npm ci'
        }
      }
    }
  }
  post {
    always {
      // allowEmptyResults, because a repository with no tests yet is not a broken pipeline.
      junit allowEmptyResults: true, testResults: '**/junit*.xml, **/test-results/**/*.xml'
      // A stale workspace produces failures that belong to the last build, not this one.
      cleanWs()
    }
  }
}
