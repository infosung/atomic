package com.infosung.atomic.contract.workflow

import java.nio.file.Files
import java.nio.file.Path
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

object WorkflowContractFixtures {
  fun readWorkflow(path: String): String = Files.readString(findRepoRoot().resolve(path))

  fun readCiVerifyJob(path: String): CiWorkflowVerifyJob {
    val root =
        Yaml(LoaderOptions()).load<Map<String, Any?>>(readWorkflow(path))
            ?: error("Failed to parse workflow yaml at $path")
    val jobs = root.requiredMap("jobs", path)
    val verify = jobs.requiredMap("verify", path)
    val strategy = verify.requiredMap("strategy", path)
    val matrix = strategy.requiredMap("matrix", path)
    val include =
        matrix["include"] as? List<*>
            ?: error("Workflow $path does not define jobs.verify.strategy.matrix.include")
    val steps =
        verify["steps"] as? List<*> ?: error("Workflow $path does not define jobs.verify.steps")
    return CiWorkflowVerifyJob(
        include =
            include.mapIndexed { index, lane ->
              val laneMap =
                  lane as? Map<*, *>
                      ?: error(
                          "Workflow $path matrix lane[$index] must be an object, but was ${lane?.javaClass?.name}")
              CiWorkflowVerifyLane(
                  springboot = laneMap.requiredString("springboot", path, index),
                  kotlin = laneMap.requiredString("kotlin", path, index),
                  label = laneMap.requiredString("label", path, index),
              )
            },
        steps =
            steps.mapIndexed { index, step ->
              val stepMap =
                  step as? Map<*, *>
                      ?: error(
                          "Workflow $path verify step[$index] must be an object, but was ${step?.javaClass?.name}")
              CiWorkflowVerifyStep(
                  name = stepMap.requiredString("name", path, index),
                  ifExpression = stepMap["if"] as? String,
                  run = stepMap["run"] as? String,
              )
            },
    )
  }

  private fun findRepoRoot(): Path {
    var current = Path.of("").toAbsolutePath()
    while (true) {
      if (Files.exists(current.resolve(".github/workflows"))) {
        return current
      }
      current.parent?.let { current = it }
          ?: error("Failed to locate repository root from ${Path.of("").toAbsolutePath()}")
    }
  }

  private fun Map<*, *>.requiredMap(key: String, path: String): Map<*, *> =
      this[key] as? Map<*, *> ?: error("Workflow $path is missing object property '$key'")

  private fun Map<*, *>.requiredString(key: String, path: String, laneIndex: Int): String =
      this[key] as? String
          ?: error("Workflow $path matrix lane[$laneIndex] is missing string property '$key'")
}

data class CiWorkflowVerifyJob(
    val include: List<CiWorkflowVerifyLane>,
    val steps: List<CiWorkflowVerifyStep>,
)

data class CiWorkflowVerifyLane(
    val springboot: String,
    val kotlin: String,
    val label: String,
)

data class CiWorkflowVerifyStep(
    val name: String,
    val ifExpression: String?,
    val run: String?,
)
