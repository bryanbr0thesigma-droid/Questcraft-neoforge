# Python 2 (MCP 9.40 is). Usage: python2 reobf.py <mcp940>
# Vivecraft's build.py without its installer step: recompile, then reobfuscate to Minecraft's names
import os, sys
mcp_dir = os.path.abspath(sys.argv[1])
sys.path.append(mcp_dir)
os.chdir(mcp_dir)
from runtime.mcp import recompile_side, reobfuscate_side
from runtime.commands import Commands, CLIENT
commands = Commands(None, verify=True)
recompile_side(commands, CLIENT)
commands.creatergcfg(reobf=True, keep_lvt=True, keep_generics=True, srg_names=False)
reobfuscate_side(commands, CLIENT)
