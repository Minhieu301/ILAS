import runpy
import sys
import traceback
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

def run_module(module_name):
    try:
        print(f"RUNNING: {module_name}...")
        runpy.run_module(module_name, run_name="__main__")
    except Exception as e:
        print(f"ERROR in {module_name}:")
        traceback.print_exc()
        raise

if __name__ == "__main__":
    print("REBUILDING ALL AI COMPONENTS...")
    
    try:
        # 1) Build vector store
        run_module("ai.build_vector_store_articles")
        run_module("ai.build_vector_store_chunks")
        run_module("ai.build_vector_store_simplified")

        # 2) Build BM25
        run_module("ai.bm25_index")

        # 3) Build topic clusters
        run_module("ai.topic_cluster_builder")

        print("DONE! ALL MODELS & INDEXES REBUILT SUCCESSFULLY.")
        
    except Exception as e:
        print(f"REBUILD FAILED: {e}")
        sys.exit(1)
