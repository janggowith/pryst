import sys
import os
import argparse
from pathlib import Path
from antlr4 import *
from antlr4.tree.Trees import Trees

import llvmlite.binding as llvm
from pryst.generated.PrystLexer import PrystLexer
from pryst.generated.PrystParser import PrystParser
from pryst.compiler.listener.ProgramListener import ProgramListener


def optimize(module_ref):
    pass_manager_builder = llvm.create_pass_manager_builder()

    module_pass_manager = llvm.create_module_pass_manager()

    # Optimize the fuck out of this
    module_pass_manager.add_constant_merge_pass()
    module_pass_manager.add_dead_arg_elimination_pass()
    module_pass_manager.add_function_attrs_pass()
    module_pass_manager.add_function_inlining_pass(5)
    module_pass_manager.add_global_dce_pass()
    module_pass_manager.add_global_optimizer_pass()
    module_pass_manager.add_ipsccp_pass()
    module_pass_manager.add_dead_code_elimination_pass()
    module_pass_manager.add_cfg_simplification_pass()
    module_pass_manager.add_gvn_pass()
    module_pass_manager.add_instruction_combining_pass()
    module_pass_manager.add_licm_pass()
    module_pass_manager.add_sccp_pass()
    module_pass_manager.add_sroa_pass()
    module_pass_manager.add_type_based_alias_analysis_pass()
    module_pass_manager.add_basic_alias_analysis_pass()

    pass_manager_builder.populate(module_pass_manager)

    module_pass_manager.run(module_ref)


def generate_object_file(module, output_name):
    llvm.initialize()
    llvm.initialize_native_target()
    llvm.initialize_native_asmprinter()
    target = llvm.Target.from_default_triple()

    target_machine = target.create_target_machine(opt=3)
    module_ref = llvm.parse_assembly(str(module))

    optimize(module_ref)

    obj = target_machine.emit_object(module_ref)

    with open("./out/program.o", "wb") as f:
        f.write(obj)


def main():
    argParser = argparse.ArgumentParser()
    argParser.add_argument('filename', type=str, nargs='?',
                           help='Path to the script file.')
    argParser.add_argument('--tokens',  dest='parse_tree',  action='store_true',
                           help='Show string representation of a parse tree for the input')
    #
    # Parse arguments
    #
    args = argParser.parse_args()
    with open(args.filename) as file_contents:
        content = file_contents.read()
    if Path(args.filename).suffix == '.pst':
        os.system('rm -rf out')
        os.system('mkdir out')
        os.system('as ./bootstrap/start.s -o ./out/start.o')
        os.system(
            'clang -shared -O3 ./bootstrap/stdlib.c -o ./out/stdlib.o')
        input_stream = InputStream(content)
        lexer = PrystLexer(input_stream)
        tokens = CommonTokenStream(lexer)

        parser = PrystParser(tokens)
        tree = parser.program()
        # Print parse trees if need (full or flattened)
        if args.parse_tree:
            parseTreeString = Trees.toStringTree(tree, recog=parser)
            print(parseTreeString)

        printer = ProgramListener()
        walker = ParseTreeWalker()
        walker.walk(printer, tree)
        generate_object_file(printer.module, args.filename)
        os.system(
            'ld ./out/program.o ./out/start.o ./out/stdlib.o -o ' + str(Path(args.filename))[:-4])
        os.system('objdump -d ' + str(Path(args.filename))[:-4])
    else:
        exit(1)  # Say is your extension .pst


if __name__ == '__main__':
    main()
