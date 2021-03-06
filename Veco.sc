
Veco {
	// there is two kind of project management:
	// - project_slots which hold a dictionary slot_index -> project_env
	// - projects which hold a dictionary path -> project_env

	classvar <>fxgroup;
	classvar <>projects;
	classvar <>project_slots;
	classvar <>extension_path;
	classvar <>user_path;
	classvar <>file_editor;
	classvar <>save_hook; // used by shortcut_clip to load data in the virtual clip instead of the target one
	classvar <>last_activated_project;
	classvar <>previous_activated_project;

	*initClass {
		projects = Dictionary.new;
		project_slots = Array.newClear(8);
		project_slots[0] = topEnvironment;
		Class.initClassTree(Platform); // needed ?
		Class.initClassTree(ControlSpec); // needed ?
		extension_path = Platform.userExtensionDir +/+ "seco/seco/veco";
		user_path = "~/code/sc/seco/vlive/".standardizePath;
	}

	*force_init {
		(extension_path +/+ "main.scd").standardizePath.load;
		^this.main;
	}

	*main { 
		^ ~veco
	}

	*gui {
		~launchpad_gui.make_gui;
	}

	*relpath_to_abspath { arg path;
		if(PathName(path).isRelativePath) {
			path = user_path +/+ path
		};
		^path;
	}

	*open_project { arg path, activate;
		// open project in current environment
		this.init;
		path = this.relpath_to_abspath(path);
		~veco_project_manager.open_project(path, activate);
	}

	*open_main_project { arg path;
		//topEnvironment.push; // switch project is used
		this.open_project(path);
	}

	*activate_main_project { arg change_vim_path=true;
		activate_project_by_env(topEnvironment, change_vim_path);
	}

	*activate_project_by_env { arg env, change_vim_path=true;
		env.push;
		~veco.clip.activate;
		this.change_vim_path(~veco.project_path, change_vim_path);
		if(env !== last_activated_project) {
			previous_activated_project = last_activated_project;
			last_activated_project = env;
		};
	}

	*open_side_project { arg path, activate=false;
		path = this.relpath_to_abspath(path);
		if(projects[path].isNil) {
			projects[path] = Environment.new;
			projects[path].parent = topEnvironment;
			projects[path][\veco] = nil;
		};
		projects[path].use({
			this.open_project(path, activate);
		});
		^projects[path];
	}

	*close_side_project { arg path;
		path = this.relpath_to_abspath(path);
		if(projects[path].notNil) {
			projects[path].use({
				Veco.main.project_destructor;
			});
		};
	}

	*activate_side_project { arg path, change_vim_path=true;
		// activate project by path
		path = this.relpath_to_abspath(path);
		if(path == topEnvironment[\veco_project_path]) {
			this.activate_main_project;
		} {
			if(projects[path].notNil) {
				this.activate_project_by_env(projects[path]);
			};
		}
	}

	*change_vim_path { arg path, do_it=true;
		// FIXME: use a vim class
		if(do_it == true) {
			"vim --servername scvim --remote-send '<Esc>:cd %<Enter>'".format(path).unixCmd;
		};
	}

	*switch_project_slot { arg num, change_vim_path=true;
		var env = project_slots[num];
		var path;
		if(env.isNil) {
			if(num == 0) {
				// should not because already initialized
				// FIXME: what if no main project yet ? bug
				project_slots[num] = topEnvironment;
				this.activate_project_by_env(project_slots[num]);
			} {
				project_slots[num] = Environment.new;
				project_slots[num].parent = topEnvironment;
				project_slots[num][\veco] = nil;
				project_slots[num].push;
				path = this.relpath_to_abspath("start");
				this.open_project(path);
			};
			this.change_vim_path(~veco.project_path, change_vim_path);
		} {
			this.activate_project_by_env(project_slots[num]);
		};
	
	}

	//*init_groups {
	//	fxgroup = Group.after(1);
	//}

	*new { arg name, pat;
		var node = this.main.get_node_by_uname(name);
		^node;
	}

	*init { arg initfun;
		if(this.main.isNil) {
			this.force_init(initfun)
		}
	}

	*do { arg fun;
		var name = ~name;
		var namex = ~namex;
		var index = ~index;
		fun.(name, namex, index);
	}

	*save { arg uname;
		//^main.get_nodeclip_by_uname(uname);
		if(save_hook.notNil) {
			^save_hook.(uname)
		} {
			^this.main.get_node_by_uname(uname).data;
		}
	}

	*load_file { arg path;
		^ (this.main.project_path +/+ path).load;
	}

	*exec_file { arg path;
		path.debug("exec_file");
		^ ~execute_till_special_end.((this.main.project_path +/+ path));
	}

	*load_lib { arg path;
		//(this.main.lib_path +/+ path).load;
		^ ~execute_till_special_end.((this.main.lib_path +/+ path));
	}

}


BufDef {
	classvar <>client = \veco;
	classvar <>all;
	classvar <>root;

	*initClass {
		all = IdentityDictionary.new;
		root = "~/Musique/sc/samplekit".standardizePath;
	}

	*new { arg name, path;
		path = this.my_new(name, path);
		path.debug("BufDef.new: path");
		^BufferPool.get_stereo_sample(client, path);
	}

	*mono { arg name, path;
		path = this.my_new(name, path);
		^BufferPool.get_mono_sample(client, path);
	}

	*my_new { arg name, path;
		if(path.isNil) {
			path = all.at(name);
			path = this.relpath_to_abspath(path);
		} {
			all.put(name, path);
			path = this.relpath_to_abspath(path);
		};
		^path;
	}

	*relpath_to_abspath { arg path;
		if(PathName(path).isRelativePath) {
			^(root +/+ path)
		} {
			^path
		}
	}

	*freeClient {
		BufferPool.release_client(client)
	}

	*freeAll {
		BufferPool.reset;	
	}

}

WavetableDef : BufDef {

	classvar <>client = \veco;
	classvar <>all;
	classvar <>root;

	*initClass {
		all = IdentityDictionary.new;
		root = "~/Musique/sc/samplekit/wavetable".standardizePath;
	}
	
	*new { arg name, path;
		path = this.my_new(name, path);
		path.debug("WavetableDef.new: path");
		^BufferPool.get_wavetable_sample(client, path);
	}


}

BusDef {
	
	classvar <>client = \veco;
	classvar <>all;
	classvar <>root;

	*initClass {
		all = IdentityDictionary.new;
		root = "~/Musique/sc/samplekit".standardizePath;
	}

	*new { arg name, rate, channels;
		var bus;

		//if(~veco_project_path.notNil) {
		//	client = ~veco_project_path;
		//}

		if(all.at(name).notNil or: {rate.isNil}) {
			bus = all.at(name)
		} {
			if(channels.isNil) {
				if(rate == \audio) {
					channels = 2
				} {
					channels = 1
				};
			};
			bus = Bus.alloc(rate, Server.default, channels);
			this.watchServer(Server.default);
			all.put(name, bus);
		};
		^bus;
	}

	*free { arg name;
		all.at(name).free;
		all.put(name, nil);
	}

	*freeClient {
		this.freeAll;
	}

	*freeAll {
		all.do { _.free };	
		all = IdentityDictionary.new;
	}

	*watchServer { |server|
		if(NotificationCenter.registrationExists(server,\newAllocators,this).not,{
			NotificationCenter.register(server,\newAllocators,this,{
				this.freeAll;
			});
		});
	}

}

GroupDef {
	classvar <>groupdict;

	*initClass {
		groupdict = Dictionary.new;
	}

	*newGroup { arg target, addaction;
		^Group.new(target, addaction)
	}

	*new { arg name, target, addaction='addToHead';
		var group;
		target = target ? Server.default;
		if(groupdict[name].isNil or: { groupdict[name].isPlaying.not }) {
			groupdict[name] = this.newGroup(target, addaction);
			groupdict[name].register(true);
		};
		^groupdict[name]
	}
}

ParGroupDef : GroupDef {
	*newGroup { arg target, addaction;
		^ParGroup.new(target, addaction)
	}
}


Sdef {
	//classvar storage;
	//classvar specs;

	*initClass {
		///storage = IdentityDictionary.new;
		///specs = IdentityDictionary.new;
	}
	
	*new { arg node_uname, name, kind, spec;
		var bus;
		var storage = ~score_storage;
		if(kind.isNil) {
			^storage.get(node_uname, name)
		} {
			^storage.define(node_uname, name, kind, spec);
		}
	}

	*force { arg node_uname, name, kind, spec;
		var bus;
		var storage = ~score_storage;
		if(kind.isNil) {
			^storage.get(node_uname, name)
		} {
			^storage.define(node_uname, name, kind, spec, true);
		}
	}

	*load_data_from_clip { arg source_clip, dest_clip;
		var storage = ~score_storage;
		^storage.load_data_from_clip(source_clip, dest_clip);
	}

	*load_clip_data { arg clip;
		var storage = ~score_storage;
		^storage.load_clip_data(clip);


	}
}




+ NodeProxy {
	isource_ { arg obj;
		if(this.isPlaying == true) {
			this.put(nil, obj, 0)
		} {
			this.put(nil, obj, 0, nil, false);
		}
	}
}

+ Boolean {
	blend { arg that, blendfact;
		if(blendfact < 0.5) {
			^this
		} {
			^that
		}
	}
}


//Sdef {
//	classvar storage;
//	classvar specs;
//
//	*initClass {
//		storage = IdentityDictionary.new;
//		specs = IdentityDictionary.new;
//	}
//	
//	*new { arg node_name, name, kind, spec;
//		var bus;
//		switch(kind, 
//			nil, {
//				^Pfunc({storage[node_name+++name]});
//			},
//			\var, {
//				storage[node_name+++name] = storage[node_name+++name] ?? 1;
//				specs[node_name+++name] = spec;
//				^storage[node_name+++name];
//			},
//			\sampler, {
//
//			}
//		);
//	}
//
//	*pbind { arg node_name, name, key;
//		key = key ?? name;
//		Pbind(key, Pfunc({storage[node_name+++name]}))
//	}
//
//	*get_value { arg node_name, name, val;
//		^storage[node_name+++name]
//	}
//
//	*set_value { arg node_name, name, val;
//		storage[node_name+++name] = val;
//		^storage[node_name+++name]
//	}
//
//	*edit { arg node_name, name, spec;
//		spec = spec ?? specs[node_name+++name];
//		~edit_sdef_variable.(node_name, name, spec);
//	}
//
//
//
//}
