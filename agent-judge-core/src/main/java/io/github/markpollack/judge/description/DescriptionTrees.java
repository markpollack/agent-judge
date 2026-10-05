package io.github.markpollack.judge.description;
import java.util.*;
import io.github.markpollack.judge.portable.PortableForm;
final class DescriptionTrees {
	static Map<String, Object> juryTree(JuryDescription description) {
		return switch (description) {
			case SimpleJuryDescription simple -> simple.portableTree();
			case CascadedJuryDescription cascaded -> cascaded.portableTree();
			case MetaJuryDescription meta -> meta.portableTree();
			case OpaqueJuryDescription opaque -> opaque.portableTree();
			default -> {
				var tree = new LinkedHashMap<>(description.toPortable());
				tree.remove(PortableForm.DESCRIPTION_VERSION);
				yield tree;
			}
		};
	}

}
