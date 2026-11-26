import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
public class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var fm=compiler.getStandardFileManager(diagnostics,null,null);var paths=Files.walk(Path.of(args[0]))) {
            var files=paths.filter(p->p.toString().endsWith(".java")).map(Path::toFile).toList();
            var task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("--release","21","-proc:none"),null,fm.getJavaFileObjectsFromFiles(files));
            task.parse();
            diagnostics.getDiagnostics().forEach(System.out::println);
            if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))System.exit(1);
            System.out.println("Parsed "+files.size()+" Java source files (syntax only; not type-checked).");
        }
    }
}
