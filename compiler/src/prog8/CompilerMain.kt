package prog8

import kotlinx.cli.*
import prog8.ast.base.AstException
import prog8.compiler.CompilationResult
import prog8.compiler.compileProgram
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compiler.target.CompilationTarget
import prog8.parser.ParsingFailedError
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.time.LocalDateTime
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    printSoftwareHeader("compiler")

    compileMain(args)
}

internal fun printSoftwareHeader(what: String) {
    val buildVersion = object {}.javaClass.getResource("/version.txt").readText().trim()
    println("\nProg8 $what v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}


fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


private fun compileMain(args: Array<String>) {
    val cli = CommandLineInterface("prog8compiler")
    val startEmulator by cli.flagArgument("-emu", "auto-start emulator after successful compilation")
    val outputDir by cli.flagValueArgument("-out", "directory", "directory for output files instead of current directory", ".")
    val dontWriteAssembly by cli.flagArgument("-noasm", "don't create assembly code")
    val dontOptimize by cli.flagArgument("-noopt", "don't perform any optimizations")
    val watchMode by cli.flagArgument("-watch", "continuous compilation mode (watches for file changes), greatly increases compilation speed")
    val compilationTarget by cli.flagValueArgument("-target", "compilertarget",
            "target output of the compiler, currently '${C64Target.name}' and '${Cx16Target.name}' available", C64Target.name)
    val moduleFiles by cli.positionalArgumentsList("modules", "main module file(s) to compile", minArgs = 1)

    try {
        cli.parse(args)
    } catch (e: Exception) {
        exitProcess(1)
    }

    val outputPath = pathFrom(outputDir)
    if(!outputPath.toFile().isDirectory) {
        System.err.println("Output path doesn't exist")
        exitProcess(1)
    }

    if(watchMode && moduleFiles.size<=1) {
        val watchservice = FileSystems.getDefault().newWatchService()

        while(true) {
            val filepath = pathFrom(moduleFiles.single()).normalize()
            println("Continuous watch mode active. Main module: $filepath")

            try {
                val compilationResult = compileProgram(filepath, !dontOptimize, !dontWriteAssembly, compilationTarget, outputPath)
                println("Imported files (now watching:)")
                for (importedFile in compilationResult.importedFiles) {
                    print("  ")
                    println(importedFile)
                    importedFile.parent.register(watchservice, StandardWatchEventKinds.ENTRY_MODIFY)
                }
                println("[${LocalDateTime.now().withNano(0)}]  Waiting for file changes.")
                val event = watchservice.take()
                for(changed in event.pollEvents()) {
                    val changedPath = changed.context() as Path
                    println("  change detected: $changedPath")
                }
                event.reset()
                println("\u001b[H\u001b[2J")      // clear the screen
            } catch (x: Exception) {
                throw x
            }
        }

    } else {
        for(filepathRaw in moduleFiles) {
            val filepath = pathFrom(filepathRaw).normalize()
            val compilationResult: CompilationResult
            try {
                compilationResult = compileProgram(filepath, !dontOptimize, !dontWriteAssembly, compilationTarget, outputPath)
                if(!compilationResult.success)
                    exitProcess(1)
            } catch (x: ParsingFailedError) {
                exitProcess(1)
            } catch (x: AstException) {
                exitProcess(1)
            }

            if (startEmulator) {
                if (compilationResult.programName.isEmpty())
                    println("\nCan't start emulator because no program was assembled.")
                else if(startEmulator) {
                    CompilationTarget.instance.machine.launchEmulator(compilationResult.programName)
                }
            }
        }
    }
}
